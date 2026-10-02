package com.dayan.platform.service.impl;

import com.dayan.platform.common.api.ErrorCode;
import com.dayan.platform.common.exception.BusinessException;
import com.dayan.platform.dto.CloudStorageDtos.CloudStorageRequest;
import com.dayan.platform.model.CloudStorage;
import com.dayan.platform.repository.mapper.CloudStorageMapper;
import com.dayan.platform.repository.storage.CloudStorageProbe;
import com.dayan.platform.repository.storage.CloudStorageProbe.Connection;
import com.dayan.platform.repository.storage.CloudStorageProbe.Usage;
import com.dayan.platform.service.CloudStorageService;
import com.dayan.platform.vo.CloudStorageViews.CloudStorageOverview;
import com.dayan.platform.vo.CloudStorageViews.CloudStorageView;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CloudStorageServiceImpl implements CloudStorageService {

    private static final String NEVER = "NEVER";
    private static final String AVAILABLE = "AVAILABLE";
    private static final String UNAVAILABLE = "UNAVAILABLE";

    private final CloudStorageMapper storageMapper;
    private final StorageCredentialCipher credentialCipher;
    private final CloudStorageProbe storageProbe;

    public CloudStorageServiceImpl(
            CloudStorageMapper storageMapper,
            StorageCredentialCipher credentialCipher,
            CloudStorageProbe storageProbe
    ) {
        this.storageMapper = storageMapper;
        this.credentialCipher = credentialCipher;
        this.storageProbe = storageProbe;
    }

    @Override
    public List<CloudStorageView> list() {
        return storageMapper.selectAll().stream().map(this::toView).toList();
    }

    @Override
    public CloudStorageOverview overview() {
        List<CloudStorage> storages = storageMapper.selectAll();
        Map<String, Long> providerCounts = new LinkedHashMap<>();
        for (CloudStorage storage : storages) {
            providerCounts.merge(storage.getProvider(), 1L, Long::sum);
        }
        return new CloudStorageOverview(
                storages.size(),
                storages.stream().filter(item -> Boolean.TRUE.equals(item.getEnabled())).count(),
                storages.stream().filter(item -> AVAILABLE.equals(item.getStatus())).count(),
                storages.stream().mapToLong(item -> value(item.getUsageBytes())).sum(),
                storages.stream().mapToLong(item -> value(item.getObjectCount())).sum(),
                providerCounts
        );
    }

    @Override
    @Transactional
    public CloudStorageView create(CloudStorageRequest request, long userId) {
        requireUnique(request.storageKey(), request.name(), null);
        requireCredentials(request.accessKey(), request.secretKey());
        CloudStorage storage = new CloudStorage();
        apply(storage, request, true);
        storage.setDefaultStorage(storageMapper.selectAll().isEmpty());
        storage.setStatus(NEVER);
        storage.setUsageBytes(0L);
        storage.setObjectCount(0L);
        storage.setCreatedBy(userId);
        storageMapper.insert(storage);
        return toView(requireStorage(storage.getId()));
    }

    @Override
    @Transactional
    public CloudStorageView update(long id, CloudStorageRequest request) {
        CloudStorage storage = requireStorage(id);
        if (Boolean.TRUE.equals(storage.getDefaultStorage()) && !request.enabled()) {
            throw new BusinessException(ErrorCode.CONFLICT, "Default storage cannot be disabled");
        }
        requireUnique(request.storageKey(), request.name(), id);
        apply(storage, request, false);
        storage.setStatus(NEVER);
        storage.setLastCheckMessage(null);
        storage.setLastCheckLatencyMs(null);
        storage.setLastCheckedAt(null);
        storageMapper.updateById(storage);
        return toView(requireStorage(id));
    }

    @Override
    public CloudStorageView test(long id) {
        CloudStorage storage = requireStorage(id);
        String accessKey = credentialCipher.decrypt(storage.getAccessKeyCiphertext());
        String secretKey = credentialCipher.decrypt(storage.getSecretKeyCiphertext());
        long started = System.nanoTime();
        OffsetDateTime checkedAt = OffsetDateTime.now(ZoneOffset.UTC);
        String status = AVAILABLE;
        String message = "Connection successful";
        long usageBytes = value(storage.getUsageBytes());
        long objectCount = value(storage.getObjectCount());
        try {
            Usage usage = storageProbe.probe(new Connection(
                    storage.getProvider(),
                    storage.getEndpoint(),
                    storage.getRegion(),
                    storage.getBucket(),
                    accessKey,
                    secretKey
            ));
            usageBytes = usage.bytes();
            objectCount = usage.objects();
        } catch (RuntimeException exception) {
            status = UNAVAILABLE;
            message = safeFailureMessage(exception, accessKey, secretKey);
        }
        long latencyMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
        storageMapper.updateCheckResult(
                id,
                status,
                message,
                latencyMs,
                usageBytes,
                objectCount,
                checkedAt
        );
        return toView(requireStorage(id));
    }

    @Override
    @Transactional
    public CloudStorageView setDefault(long id) {
        CloudStorage storage = requireStorage(id);
        if (!Boolean.TRUE.equals(storage.getEnabled())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Disabled storage cannot be set as default");
        }
        storageMapper.clearOtherDefaults(id);
        storageMapper.markDefault(id);
        return toView(requireStorage(id));
    }

    @Override
    @Transactional
    public void delete(long id) {
        CloudStorage storage = requireStorage(id);
        if (Boolean.TRUE.equals(storage.getDefaultStorage())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Default storage cannot be deleted");
        }
        storageMapper.deleteById(id);
    }

    private void apply(CloudStorage storage, CloudStorageRequest request, boolean creating) {
        storage.setStorageKey(request.storageKey().trim().toLowerCase(Locale.ROOT));
        storage.setName(request.name().trim());
        storage.setProvider(request.provider().trim().toUpperCase(Locale.ROOT));
        storage.setEndpoint(normalizeEndpoint(request.endpoint()));
        storage.setRegion(normalizeNullable(request.region()));
        storage.setBucket(request.bucket().trim());
        storage.setEnabled(request.enabled());
        if (StringUtils.hasText(request.accessKey())) {
            storage.setAccessKeyCiphertext(credentialCipher.encrypt(request.accessKey().trim()));
        }
        if (StringUtils.hasText(request.secretKey())) {
            storage.setSecretKeyCiphertext(credentialCipher.encrypt(request.secretKey()));
        }
        if (!creating && (
                !StringUtils.hasText(storage.getAccessKeyCiphertext())
                        || !StringUtils.hasText(storage.getSecretKeyCiphertext())
        )) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Storage credentials are required");
        }
    }

    private String normalizeEndpoint(String endpoint) {
        String value = endpoint.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        try {
            URI uri = new URI(value);
            if (!List.of("http", "https").contains(uri.getScheme()) || !StringUtils.hasText(uri.getHost())) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Storage endpoint must be an HTTP URL");
            }
            return value;
        } catch (URISyntaxException exception) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Storage endpoint is invalid");
        }
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void requireCredentials(String accessKey, String secretKey) {
        if (!StringUtils.hasText(accessKey) || !StringUtils.hasText(secretKey)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Storage credentials are required");
        }
    }

    private void requireUnique(String storageKey, String name, Long excludeId) {
        if (storageMapper.countByStorageKey(storageKey.trim().toLowerCase(Locale.ROOT), excludeId) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "Storage key already exists");
        }
        if (storageMapper.countByName(name.trim(), excludeId) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "Storage name already exists");
        }
    }

    private CloudStorage requireStorage(long id) {
        CloudStorage storage = storageMapper.selectById(id);
        if (storage == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Cloud storage not found");
        }
        return storage;
    }

    private CloudStorageView toView(CloudStorage storage) {
        String accessKey = credentialCipher.decrypt(storage.getAccessKeyCiphertext());
        return new CloudStorageView(
                storage.getId(),
                storage.getStorageKey(),
                storage.getName(),
                storage.getProvider(),
                storage.getEndpoint(),
                storage.getRegion(),
                storage.getBucket(),
                accessKeyHint(accessKey),
                StringUtils.hasText(storage.getAccessKeyCiphertext())
                        && StringUtils.hasText(storage.getSecretKeyCiphertext()),
                Boolean.TRUE.equals(storage.getDefaultStorage()),
                Boolean.TRUE.equals(storage.getEnabled()),
                storage.getStatus(),
                storage.getLastCheckMessage(),
                storage.getLastCheckLatencyMs(),
                value(storage.getUsageBytes()),
                value(storage.getObjectCount()),
                storage.getLastCheckedAt(),
                storage.getCreatedAt(),
                storage.getUpdatedAt()
        );
    }

    private String accessKeyHint(String accessKey) {
        if (!StringUtils.hasText(accessKey)) {
            return null;
        }
        int visibleStart = Math.max(0, accessKey.length() - 4);
        return "****" + accessKey.substring(visibleStart);
    }

    private String safeFailureMessage(RuntimeException failure, String accessKey, String secretKey) {
        Throwable root = failure;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String message = StringUtils.hasText(root.getMessage())
                ? root.getMessage()
                : "Connection failed";
        if (StringUtils.hasText(accessKey)) {
            message = message.replace(accessKey, "****");
        }
        if (StringUtils.hasText(secretKey)) {
            message = message.replace(secretKey, "****");
        }
        return message.length() > 480 ? message.substring(0, 480) : message;
    }

    private long value(Long value) {
        return value == null ? 0 : value;
    }
}

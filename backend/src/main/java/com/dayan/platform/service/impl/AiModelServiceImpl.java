package com.dayan.platform.service.impl;

import com.dayan.platform.common.api.ErrorCode;
import com.dayan.platform.common.exception.BusinessException;
import com.dayan.platform.dto.AiModelDtos.ModelRequest;
import com.dayan.platform.dto.AiModelDtos.ModelType;
import com.dayan.platform.model.AiModel;
import com.dayan.platform.repository.mapper.AiModelMapper;
import com.dayan.platform.service.AiModelService;
import com.dayan.platform.vo.AiModelViews.ModelSummary;
import com.dayan.platform.vo.AiModelViews.ModelTestResult;
import com.dayan.platform.vo.PageResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AiModelServiceImpl implements AiModelService {

    private static final int TEST_RESPONSE_LIMIT = 300;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final AiModelMapper modelMapper;
    private final ModelCredentialCipher credentialCipher;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AiModelServiceImpl(
            AiModelMapper modelMapper,
            ModelCredentialCipher credentialCipher,
            ObjectMapper objectMapper
    ) {
        this.modelMapper = modelMapper;
        this.credentialCipher = credentialCipher;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ModelSummary> page(
            int page,
            int size,
            String keyword,
            ModelType modelType,
            Boolean enabled
    ) {
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        String type = modelType == null ? null : modelType.name();
        long total = modelMapper.countPage(normalizedKeyword, type, enabled);
        List<ModelSummary> items = modelMapper.selectPage(
                normalizedKeyword,
                type,
                enabled,
                (long) (page - 1) * size,
                size
        ).stream().map(this::summary).toList();
        return PageResponse.of(page, size, total, items);
    }

    @Override
    @Transactional(readOnly = true)
    public ModelSummary get(long id) {
        return summary(requireModel(id));
    }

    @Override
    @Transactional
    public ModelSummary create(ModelRequest request) {
        if (!StringUtils.hasText(request.accessKey()) && !StringUtils.hasText(request.secretKey())) {
            throw invalid("At least one model credential is required");
        }
        AiModel model = new AiModel();
        apply(model, request, false);
        model.setLastTestStatus("NEVER");
        try {
            modelMapper.insert(model);
        } catch (DataIntegrityViolationException exception) {
            throw conflict("A model with the same manufacturer and name already exists");
        }
        return summary(model);
    }

    @Override
    @Transactional
    public ModelSummary update(long id, ModelRequest request) {
        AiModel model = requireModel(id);
        apply(model, request, true);
        if (Boolean.TRUE.equals(model.getEnabled())) {
            requireCredentials(model);
        }
        model.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        try {
            modelMapper.updateById(model);
        } catch (DataIntegrityViolationException exception) {
            throw conflict("A model with the same manufacturer and name already exists");
        }
        return summary(model);
    }

    @Override
    @Transactional
    public ModelSummary changeStatus(long id, boolean enabled) {
        AiModel model = requireModel(id);
        if (enabled) {
            requireCredentials(model);
        }
        model.setEnabled(enabled);
        model.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        modelMapper.updateById(model);
        return summary(model);
    }

    @Override
    @Transactional
    public void delete(long id) {
        requireModel(id);
        try {
            modelMapper.deleteById(id);
        } catch (DataIntegrityViolationException exception) {
            throw conflict("Models used by Agents cannot be deleted");
        }
    }

    @Override
    @Transactional
    public ModelTestResult test(long id) {
        AiModel model = requireModel(id);
        if (!Boolean.TRUE.equals(model.getEnabled())) {
            throw conflict("Disabled models cannot be tested");
        }

        OffsetDateTime testedAt = OffsetDateTime.now(ZoneOffset.UTC);
        long startedAt = System.nanoTime();
        boolean success = false;
        String message;
        try {
            URI endpoint = publicEndpoint(model.getModelUrl());
            HttpRequest request = testRequest(model, endpoint);
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );
            success = response.statusCode() >= 200 && response.statusCode() < 300;
            message = success
                    ? "Connection succeeded (HTTP " + response.statusCode() + ")"
                    : "Provider returned HTTP " + response.statusCode() + responseMessage(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            message = "Connection test was interrupted";
        } catch (IOException | RuntimeException exception) {
            message = "Connection failed: " + safeMessage(exception);
        }

        long latencyMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
        model.setLastTestStatus(success ? "SUCCESS" : "FAILED");
        model.setLastTestMessage(truncate(message, 500));
        model.setLastTestLatencyMs(latencyMs);
        model.setLastTestedAt(testedAt);
        model.setUpdatedAt(testedAt);
        modelMapper.updateById(model);
        return new ModelTestResult(success, model.getLastTestMessage(), latencyMs, testedAt);
    }

    private void apply(AiModel model, ModelRequest request, boolean preserveBlankCredentials) {
        validateUrl(request.accessAddress(), "accessAddress");
        validateUrl(request.modelUrl(), "modelUrl");
        model.setManufacturer(request.manufacturer().trim());
        model.setName(request.name().trim());
        model.setAccessAddress(request.accessAddress().trim());
        model.setModelUrl(request.modelUrl().trim());
        model.setModelType(request.modelType().name());
        model.setEnabled(request.enabled());
        if (!preserveBlankCredentials || StringUtils.hasText(request.accessKey())) {
            model.setAccessKeyCiphertext(credentialCipher.encrypt(trimToNull(request.accessKey())));
        }
        if (!preserveBlankCredentials || StringUtils.hasText(request.secretKey())) {
            model.setSecretKeyCiphertext(credentialCipher.encrypt(trimToNull(request.secretKey())));
        }
    }

    private HttpRequest testRequest(AiModel model, URI endpoint) throws JsonProcessingException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(testBody(model)));

        String accessKey = credentialCipher.decrypt(model.getAccessKeyCiphertext());
        String secretKey = credentialCipher.decrypt(model.getSecretKeyCiphertext());
        String bearerToken = StringUtils.hasText(secretKey) ? secretKey : accessKey;
        if (StringUtils.hasText(bearerToken)) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        if (StringUtils.hasText(accessKey)) {
            builder.header("X-Access-Key", accessKey);
        }
        if (StringUtils.hasText(secretKey)) {
            builder.header("X-Secret-Key", secretKey);
        }
        return builder.build();
    }

    private String testBody(AiModel model) throws JsonProcessingException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model.getName());
        switch (ModelType.valueOf(model.getModelType())) {
            case EMBEDDING -> body.put("input", "connection test");
            case RERANK -> {
                body.put("query", "connection test");
                body.put("documents", List.of("connection test"));
            }
            case IMAGE -> body.put("prompt", "connection test");
            case AUDIO -> body.put("input", "connection test");
            case CHAT, MULTIMODAL -> {
                body.put("messages", List.of(Map.of("role", "user", "content", "ping")));
                body.put("max_tokens", 1);
            }
        }
        return objectMapper.writeValueAsString(body);
    }

    private URI publicEndpoint(String value) {
        URI uri;
        try {
            uri = new URI(value.trim());
        } catch (URISyntaxException exception) {
            throw invalid("modelUrl must be a valid HTTP or HTTPS URL");
        }
        validateUrl(uri, "modelUrl");
        try {
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (isPrivateAddress(address)) {
                    throw invalid("modelUrl must not resolve to a private or local address");
                }
            }
        } catch (UnknownHostException exception) {
            throw invalid("modelUrl host could not be resolved");
        }
        return uri;
    }

    private void validateUrl(String value, String field) {
        try {
            validateUrl(new URI(value.trim()), field);
        } catch (URISyntaxException exception) {
            throw invalid(field + " must be a valid HTTP or HTTPS URL");
        }
    }

    private void validateUrl(URI uri, String field) {
        String scheme = uri.getScheme();
        if (uri.getHost() == null
                || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))
                || uri.getUserInfo() != null) {
            throw invalid(field + " must be an HTTP or HTTPS URL without embedded credentials");
        }
    }

    private boolean isPrivateAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        return address instanceof Inet6Address && (bytes[0] & 0xfe) == 0xfc;
    }

    private AiModel requireModel(long id) {
        AiModel model = modelMapper.selectById(id);
        if (model == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Model not found");
        }
        return model;
    }

    private void requireCredentials(AiModel model) {
        if (!StringUtils.hasText(model.getAccessKeyCiphertext())
                && !StringUtils.hasText(model.getSecretKeyCiphertext())) {
            throw invalid("Configure at least one model credential before enabling the model");
        }
    }

    private ModelSummary summary(AiModel model) {
        return new ModelSummary(
                model.getId(),
                model.getManufacturer(),
                model.getName(),
                model.getAccessAddress(),
                model.getModelUrl(),
                model.getModelType(),
                StringUtils.hasText(model.getAccessKeyCiphertext()),
                StringUtils.hasText(model.getSecretKeyCiphertext()),
                Boolean.TRUE.equals(model.getEnabled()),
                model.getLastTestStatus(),
                model.getLastTestMessage(),
                model.getLastTestLatencyMs(),
                model.getLastTestedAt(),
                model.getCreatedAt(),
                model.getUpdatedAt()
        );
    }

    private String responseMessage(String body) {
        if (!StringUtils.hasText(body)) {
            return "";
        }
        return ": " + truncate(body.replaceAll("\\s+", " ").trim(), TEST_RESPONSE_LIMIT);
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return truncate(StringUtils.hasText(message) ? message : exception.getClass().getSimpleName(), 300);
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_ARGUMENT, message);
    }

    private BusinessException conflict(String message) {
        return new BusinessException(ErrorCode.CONFLICT, message);
    }
}

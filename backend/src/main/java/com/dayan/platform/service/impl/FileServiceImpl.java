package com.dayan.platform.service.impl;

import com.dayan.platform.common.api.ErrorCode;
import com.dayan.platform.common.exception.BusinessException;
import com.dayan.platform.config.MinioProperties;
import com.dayan.platform.dto.FileQuery;
import com.dayan.platform.model.StoredFile;
import com.dayan.platform.repository.mapper.StoredFileMapper;
import com.dayan.platform.repository.storage.ObjectStorage;
import com.dayan.platform.repository.storage.ObjectStorageException;
import com.dayan.platform.service.FileService;
import com.dayan.platform.vo.FileViews.FileView;
import com.dayan.platform.vo.FileViews.PreviewView;
import com.dayan.platform.vo.PageResponse;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileServiceImpl implements FileService {

    private static final Logger log = LoggerFactory.getLogger(FileServiceImpl.class);
    private static final String READY = "READY";
    private static final String DELETING = "DELETING";

    private final MinioProperties properties;
    private final ObjectStorage objectStorage;
    private final StoredFileMapper storedFileMapper;
    private final TransactionTemplate transactionTemplate;

    public FileServiceImpl(
            MinioProperties properties,
            ObjectStorage objectStorage,
            StoredFileMapper storedFileMapper,
            TransactionTemplate transactionTemplate
    ) {
        this.properties = properties;
        this.objectStorage = objectStorage;
        this.storedFileMapper = storedFileMapper;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public FileView upload(MultipartFile multipartFile, long uploaderId) {
        UploadInput upload = validate(multipartFile);
        String objectKey = objectKey();
        String etag;
        try (InputStream inputStream = multipartFile.getInputStream()) {
            etag = objectStorage.put(
                    objectKey,
                    inputStream,
                    multipartFile.getSize(),
                    upload.contentType()
            );
        } catch (IOException | ObjectStorageException exception) {
            throw storageFailure(exception);
        }

        StoredFile metadata = new StoredFile();
        metadata.setBucketName(properties.bucket());
        metadata.setObjectKey(objectKey);
        metadata.setOriginalName(upload.originalName());
        metadata.setContentType(upload.contentType());
        metadata.setSizeBytes(multipartFile.getSize());
        metadata.setEtag(etag);
        metadata.setUploaderId(uploaderId);
        metadata.setStatus(READY);

        try {
            transactionTemplate.executeWithoutResult(status -> storedFileMapper.insert(metadata));
        } catch (RuntimeException databaseFailure) {
            compensateUpload(objectKey, databaseFailure);
            throw databaseFailure;
        }
        return toView(requireFile(metadata.getId(), false));
    }

    @Override
    public PageResponse<FileView> list(FileQuery query) {
        String keyword = normalizeKeyword(query.getKeyword());
        long total = storedFileMapper.countReady(keyword);
        long offset = (long) (query.getPage() - 1) * query.getSize();
        var items = storedFileMapper.selectReadyPage(keyword, query.getSize(), offset).stream()
                .map(this::toView)
                .toList();
        return PageResponse.of(query.getPage(), query.getSize(), total, items);
    }

    @Override
    public FileView detail(long id) {
        return toView(requireFile(id, true));
    }

    @Override
    public Download download(long id) {
        StoredFile file = requireReadyFile(id);
        try {
            return new Download(
                    file.getOriginalName(),
                    file.getContentType(),
                    file.getSizeBytes(),
                    objectStorage.get(file.getObjectKey()).inputStream()
            );
        } catch (ObjectStorageException exception) {
            throw storageFailure(exception);
        }
    }

    @Override
    public PreviewView preview(long id) {
        StoredFile file = requireReadyFile(id);
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plus(properties.previewTtl());
        String disposition = ContentDisposition.inline()
                .filename(file.getOriginalName(), java.nio.charset.StandardCharsets.UTF_8)
                .build()
                .toString();
        try {
            String url = objectStorage.presignGet(
                    file.getObjectKey(),
                    disposition,
                    properties.previewTtl()
            );
            return new PreviewView(url, expiresAt);
        } catch (ObjectStorageException exception) {
            throw storageFailure(exception);
        }
    }

    @Override
    public void delete(long id) {
        StoredFile file = transactionTemplate.execute(status -> {
            StoredFile candidate = storedFileMapper.selectByIdForUpdate(id);
            if (candidate == null) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "File not found");
            }
            if (DELETING.equals(candidate.getStatus())) {
                throw new BusinessException(ErrorCode.CONFLICT, "File deletion is already in progress");
            }
            if (storedFileMapper.markDeleting(id) != 1) {
                throw new BusinessException(ErrorCode.CONFLICT, "File cannot be deleted in its current state");
            }
            candidate.setStatus(DELETING);
            return candidate;
        });

        try {
            objectStorage.remove(file.getObjectKey());
        } catch (ObjectStorageException exception) {
            recordDeleteFailure(id);
            throw storageFailure(exception);
        }

        try {
            transactionTemplate.executeWithoutResult(status -> {
                if (storedFileMapper.deleteDeleting(id) != 1) {
                    throw new IllegalStateException("File metadata deletion lost its state");
                }
            });
        } catch (RuntimeException databaseFailure) {
            recordDeleteFailure(id);
            throw databaseFailure;
        }
    }

    private UploadInput validate(MultipartFile multipartFile) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "File must not be empty");
        }
        if (multipartFile.getSize() > properties.maxFileSize().toBytes()) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE);
        }
        String contentType = normalizeContentType(multipartFile.getContentType());
        if (!properties.allowedContentTypes().contains(contentType)) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED);
        }
        return new UploadInput(SafeFileName.clean(multipartFile.getOriginalFilename()), contentType);
    }

    private StoredFile requireReadyFile(long id) {
        StoredFile file = requireFile(id, true);
        if (!READY.equals(file.getStatus())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "File not found");
        }
        return file;
    }

    private StoredFile requireFile(long id, boolean readyOnly) {
        StoredFile file = storedFileMapper.selectById(id);
        if (file == null || (readyOnly && !READY.equals(file.getStatus()))) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "File not found");
        }
        return file;
    }

    private void compensateUpload(String objectKey, RuntimeException databaseFailure) {
        try {
            objectStorage.remove(objectKey);
        } catch (RuntimeException compensationFailure) {
            databaseFailure.addSuppressed(compensationFailure);
            log.error("Failed to compensate object after metadata persistence failure: key={}", objectKey);
        }
    }

    private void recordDeleteFailure(long id) {
        try {
            transactionTemplate.executeWithoutResult(status -> storedFileMapper.markDeleteFailed(id));
        } catch (RuntimeException statusFailure) {
            log.error("Failed to record recoverable file deletion state: id={}", id, statusFailure);
        }
    }

    private String objectKey() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return "%04d/%02d/%02d/%s".formatted(
                today.getYear(),
                today.getMonthValue(),
                today.getDayOfMonth(),
                UUID.randomUUID()
        );
    }

    private String normalizeContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return "application/octet-stream";
        }
        int parameter = contentType.indexOf(';');
        String value = parameter >= 0 ? contentType.substring(0, parameter) : contentType;
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeKeyword(String keyword) {
        return StringUtils.hasText(keyword) ? keyword.trim() : null;
    }

    private BusinessException storageFailure(Exception exception) {
        log.error("Object storage operation failed", exception);
        return new BusinessException(ErrorCode.FILE_STORAGE_ERROR);
    }

    private FileView toView(StoredFile file) {
        return new FileView(
                file.getId(),
                file.getOriginalName(),
                file.getContentType(),
                file.getSizeBytes(),
                file.getEtag(),
                file.getUploaderId(),
                file.getStatus(),
                file.getCreatedAt(),
                file.getUpdatedAt()
        );
    }

    private record UploadInput(String originalName, String contentType) {
    }
}

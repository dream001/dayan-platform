package com.dayan.platform.service.impl;

import com.dayan.platform.common.api.ErrorCode;
import com.dayan.platform.common.exception.BusinessException;
import com.dayan.platform.config.MinioProperties;
import com.dayan.platform.dto.DataUploadDtos.CreateSessionRequest;
import com.dayan.platform.repository.DataUploadRepository;
import com.dayan.platform.repository.DataUploadRepository.PartData;
import com.dayan.platform.repository.DataUploadRepository.SessionData;
import com.dayan.platform.repository.storage.ObjectStorage;
import com.dayan.platform.repository.storage.ObjectStorageException;
import com.dayan.platform.service.DataUploadService;
import com.dayan.platform.vo.DataUploadViews.DatasetView;
import com.dayan.platform.vo.DataUploadViews.StorageOption;
import com.dayan.platform.vo.DataUploadViews.UploadOptions;
import com.dayan.platform.vo.DataUploadViews.UploadSessionView;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DataUploadServiceImpl implements DataUploadService {

    public static final long MULTIPART_THRESHOLD = 100L * 1024 * 1024;
    public static final int CHUNK_SIZE = 10 * 1024 * 1024;
    private static final int VIDEO_TIMEOUT_SECONDS = 600;
    private static final Map<String, Set<String>> EXTENSIONS = Map.ofEntries(
            Map.entry("MCAP", Set.of("mcap")),
            Map.entry("BAG", Set.of("bag")),
            Map.entry("VIDEO", Set.of("mp4", "webm")),
            Map.entry("AUDIO", Set.of("mp3", "wav", "aac", "ogg")),
            Map.entry("IMAGE", Set.of("jpg", "jpeg", "png")),
            Map.entry("HDF5", Set.of("h5", "hdf5")),
            Map.entry("LEROBOT", Set.of("tar")),
            Map.entry("MEITUAN", Set.of("tar")),
            Map.entry("LUMOS", Set.of("lumos")),
            Map.entry("ZC0TOUCH", Set.of("zc0touch")),
            Map.entry("SENSEXPERIENCE", Set.of("tar")),
            Map.entry("BVH", Set.of("bvh"))
    );

    private final DataUploadRepository repository;
    private final ObjectStorage objectStorage;
    private final MinioProperties storage;
    private final TransactionTemplate transactionTemplate;

    public DataUploadServiceImpl(
            DataUploadRepository repository,
            ObjectStorage objectStorage,
            MinioProperties storage,
            TransactionTemplate transactionTemplate
    ) {
        this.repository = repository;
        this.objectStorage = objectStorage;
        this.storage = storage;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public UploadOptions options(long userId) {
        return new UploadOptions(
                repository.findAccessibleProjects(userId),
                List.of(new StorageOption("minio-default", "MinIO 默认存储", "MINIO", storage.bucket())),
                MULTIPART_THRESHOLD,
                CHUNK_SIZE,
                VIDEO_TIMEOUT_SECONDS
        );
    }

    @Override
    public DatasetView uploadDirect(
            long projectId,
            String storageKey,
            String dataType,
            String sourceFingerprint,
            String robotType,
            MultipartFile file,
            long userId
    ) {
        requireProject(projectId, userId);
        requireStorage(storageKey);
        UploadInput input = validate(dataType, robotType, file.getOriginalFilename(), file.getContentType(), file.getSize());
        if (file.getSize() > MULTIPART_THRESHOLD) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE, "Large files must use multipart upload");
        }
        DatasetView duplicate = repository.findDuplicate(projectId, datasetName(input.fileName()), sourceFingerprint);
        if (duplicate != null) {
            return duplicate;
        }

        String objectKey = finalObjectKey(projectId, input.fileName());
        String etag;
        try (InputStream stream = file.getInputStream()) {
            etag = objectStorage.put(objectKey, stream, file.getSize(), input.contentType());
        } catch (IOException | ObjectStorageException exception) {
            throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR);
        }

        try {
            return transactionTemplate.execute(status -> repository.createDirectDataset(
                    projectId, storageKey, input.dataType(), input.fileName(), input.contentType(),
                    file.getSize(), objectKey, sourceFingerprint, normalizeRobotType(robotType),
                    datasetStatus(input.dataType()), userId, storage.bucket(), etag
            ));
        } catch (RuntimeException exception) {
            safelyRemove(objectKey);
            throw exception;
        }
    }

    @Override
    public UploadSessionView createSession(CreateSessionRequest request, long userId) {
        requireProject(request.projectId(), userId);
        requireStorage(request.storageKey());
        UploadInput input = validate(
                request.dataType(), request.robotType(), request.fileName(),
                request.contentType(), request.totalSize()
        );
        DatasetView duplicate = repository.findDuplicate(
                request.projectId(), datasetName(input.fileName()), request.sourceFingerprint()
        );
        if (duplicate != null) {
            return new UploadSessionView(
                    null, request.projectId(), input.dataType(), input.fileName(),
                    request.totalSize(), CHUNK_SIZE, 0, List.of(), "SUCCESS", duplicate
            );
        }

        UUID id = UUID.randomUUID();
        int chunks = Math.toIntExact((request.totalSize() + CHUNK_SIZE - 1) / CHUNK_SIZE);
        SessionData session = new SessionData(
                id, request.projectId(), request.storageKey(), input.dataType(), input.fileName(),
                input.contentType(), request.totalSize(), CHUNK_SIZE, chunks,
                finalObjectKey(request.projectId(), input.fileName()), request.sourceFingerprint(),
                normalizeRobotType(request.robotType()), "PENDING", userId,
                OffsetDateTime.now(ZoneOffset.UTC).plusHours(24)
        );
        repository.createSession(session);
        return toSessionView(session, null);
    }

    @Override
    public UploadSessionView session(UUID id, long userId) {
        return toSessionView(requireSession(id, userId), null);
    }

    @Override
    public void uploadPart(UUID id, int partNumber, MultipartFile chunk, long userId) {
        SessionData session = requireMutableSession(id, userId);
        if (partNumber < 0 || partNumber >= session.totalChunks()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Invalid part number");
        }
        long expected = partNumber == session.totalChunks() - 1
                ? session.totalSize() - (long) partNumber * session.chunkSize()
                : session.chunkSize();
        if (chunk == null || chunk.isEmpty() || chunk.getSize() != expected) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Chunk size does not match upload session");
        }
        String partKey = "uploads/%s/%06d".formatted(id, partNumber);
        try (InputStream stream = chunk.getInputStream()) {
            String etag = objectStorage.put(partKey, stream, chunk.getSize(), "application/octet-stream");
            repository.savePart(id, partNumber, partKey, chunk.getSize(), etag);
        } catch (IOException | ObjectStorageException exception) {
            repository.updateStatus(id, userId, "ERROR");
            throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR);
        }
    }

    @Override
    public void pause(UUID id, long userId) {
        requireMutableSession(id, userId);
        repository.updateStatus(id, userId, "PAUSED");
    }

    @Override
    public UploadSessionView resume(UUID id, long userId) {
        SessionData session = requireSession(id, userId);
        if (!Set.of("PAUSED", "ERROR", "PENDING", "UPLOADING").contains(session.status())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Upload session cannot be resumed");
        }
        repository.updateStatus(id, userId, "UPLOADING");
        return session(id, userId);
    }

    @Override
    public DatasetView complete(UUID id, long userId) {
        SessionData session = requireMutableSession(id, userId);
        List<PartData> parts = repository.findParts(id);
        if (parts.size() != session.totalChunks()
                || parts.stream().mapToLong(PartData::sizeBytes).sum() != session.totalSize()) {
            throw new BusinessException(ErrorCode.CONFLICT, "Upload is incomplete");
        }
        repository.updateStatus(id, userId, "COMPLETING");
        String etag;
        try {
            etag = objectStorage.compose(
                    session.objectKey(),
                    parts.stream().map(PartData::objectKey).toList(),
                    session.contentType()
            );
        } catch (ObjectStorageException exception) {
            repository.updateStatus(id, userId, "ERROR");
            throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR);
        }

        try {
            DatasetView result = transactionTemplate.execute(status -> {
                DatasetView dataset = repository.createDataset(
                        session, storage.bucket(), etag, datasetStatus(session.dataType())
                );
                repository.updateStatus(id, userId, "SUCCESS");
                return dataset;
            });
            parts.forEach(part -> safelyRemove(part.objectKey()));
            return result;
        } catch (RuntimeException exception) {
            safelyRemove(session.objectKey());
            repository.updateStatus(id, userId, "ERROR");
            throw exception;
        }
    }

    @Override
    public void cancel(UUID id, long userId) {
        SessionData session = requireSession(id, userId);
        if ("SUCCESS".equals(session.status())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Completed upload cannot be cancelled");
        }
        repository.findParts(id).forEach(part -> safelyRemove(part.objectKey()));
        repository.updateStatus(id, userId, "CANCELLED");
    }

    private UploadSessionView toSessionView(SessionData session, DatasetView duplicate) {
        return new UploadSessionView(
                session.id(), session.projectId(), session.dataType(), session.originalName(),
                session.totalSize(), session.chunkSize(), session.totalChunks(),
                repository.findUploadedParts(session.id()), session.status(), duplicate
        );
    }

    private SessionData requireSession(UUID id, long userId) {
        SessionData session = repository.findSession(id, userId);
        if (session == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Upload session not found");
        }
        return session;
    }

    private SessionData requireMutableSession(UUID id, long userId) {
        SessionData session = requireSession(id, userId);
        if (session.expiresAt().isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw new BusinessException(ErrorCode.CONFLICT, "Upload session expired");
        }
        if (Set.of("SUCCESS", "CANCELLED", "COMPLETING").contains(session.status())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Upload session is not writable");
        }
        return session;
    }

    private void requireProject(long projectId, long userId) {
        if (!repository.canUploadToProject(projectId, userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Project is unavailable or read-only");
        }
    }

    private void requireStorage(String storageKey) {
        if (!"minio-default".equals(storageKey)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Unknown cloud storage");
        }
    }

    private UploadInput validate(
            String dataType,
            String robotType,
            String originalName,
            String contentType,
            long size
    ) {
        if (size <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Empty files cannot be uploaded");
        }
        String normalizedType = dataType == null ? "" : dataType.trim().toUpperCase(Locale.ROOT);
        Set<String> extensions = EXTENSIONS.get(normalizedType);
        if (extensions == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Unsupported data type");
        }
        String fileName = SafeFileName.clean(originalName);
        int separator = fileName.lastIndexOf('.');
        String extension = separator >= 0 ? fileName.substring(separator + 1).toLowerCase(Locale.ROOT) : "";
        if (!extensions.contains(extension)) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_ALLOWED, "File extension does not match data type");
        }
        if ("HDF5".equals(normalizedType) && !StringUtils.hasText(robotType)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Robot type is required for HDF5");
        }
        String normalizedContentType = StringUtils.hasText(contentType)
                ? contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT)
                : "application/octet-stream";
        return new UploadInput(normalizedType, fileName, normalizedContentType);
    }

    private String normalizeRobotType(String robotType) {
        return StringUtils.hasText(robotType) ? robotType.trim() : null;
    }

    private String finalObjectKey(long projectId, String fileName) {
        return "datasets/%d/%s/%s".formatted(projectId, UUID.randomUUID(), fileName);
    }

    private String datasetName(String fileName) {
        int separator = fileName.lastIndexOf('.');
        return separator > 0 ? fileName.substring(0, separator) : fileName;
    }

    private String datasetStatus(String dataType) {
        return Set.of("BAG", "HDF5", "LEROBOT", "MEITUAN", "LUMOS", "ZC0TOUCH", "SENSEXPERIENCE")
                .contains(dataType) ? "PROCESSING" : "READY";
    }

    private void safelyRemove(String objectKey) {
        try {
            objectStorage.remove(objectKey);
        } catch (RuntimeException ignored) {
            // A retry or lifecycle cleanup can remove orphaned temporary objects.
        }
    }

    private record UploadInput(String dataType, String fileName, String contentType) {
    }
}

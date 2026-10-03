package com.dayan.platform.vo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class DataUploadViews {

    private DataUploadViews() {
    }

    public record ProjectOption(long id, String code, String name) {
    }

    public record StorageOption(String key, String name, String provider, String bucket) {
    }

    public record UploadOptions(
            List<ProjectOption> projects,
            List<StorageOption> storages,
            long multipartThreshold,
            int chunkSize,
            int videoTimeoutSeconds
    ) {
    }

    public record DatasetView(
            long id,
            long projectId,
            String name,
            String dataType,
            String originalName,
            String contentType,
            long sizeBytes,
            BigDecimal durationSeconds,
            String status,
            OffsetDateTime createdAt
    ) {
    }

    public record UploadSessionView(
            UUID id,
            long projectId,
            String dataType,
            String fileName,
            long totalSize,
            int chunkSize,
            int totalChunks,
            List<Integer> uploadedParts,
            String status,
            DatasetView existingDataset
    ) {
    }
}

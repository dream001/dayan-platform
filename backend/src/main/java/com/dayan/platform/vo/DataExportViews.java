package com.dayan.platform.vo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class DataExportViews {

    private DataExportViews() {
    }

    public record DatasetOption(
            long id,
            String name,
            String dataType,
            long sizeBytes,
            BigDecimal durationSeconds,
            Long projectId,
            String projectName,
            String collectorName,
            OffsetDateTime createdAt
    ) {
    }

    public record TaskView(
            long id,
            String name,
            String format,
            String status,
            int progress,
            int processedCount,
            int datasetCount,
            String configJson,
            String fileName,
            Long fileSize,
            String errorMessage,
            long creatorId,
            String creatorName,
            OffsetDateTime startedAt,
            OffsetDateTime completedAt,
            OffsetDateTime createdAt
    ) {
    }

    public record TaskDetail(TaskView task, List<DatasetOption> datasets) {
    }

    public record QuotaView(int used, int limit, int remaining) {
    }

    public record QuotaUserView(
            long userId,
            String username,
            String displayName,
            int used,
            int limit
    ) {
    }
}

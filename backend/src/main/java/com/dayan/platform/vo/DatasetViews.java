package com.dayan.platform.vo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class DatasetViews {

    private DatasetViews() {
    }

    public record DatasetView(
            long id,
            String name,
            String dataType,
            long sizeBytes,
            BigDecimal durationSeconds,
            String annotationStatus,
            Long projectId,
            String projectName,
            String robotCode,
            Long collectorId,
            String collectorName,
            String sourceTaskCode,
            long uploaderId,
            String uploaderName,
            String metadataStatus,
            boolean openShared,
            List<String> tags,
            Preview preview,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record TaskBrief(
            long id,
            String name,
            String status,
            int datasetCount,
            String creatorName,
            OffsetDateTime createdAt
    ) {
    }

    public record AnnotationSummary(
            long total,
            long qualified,
            long invalid,
            long reviewed,
            BigDecimal coveredDurationSeconds
    ) {
    }

    public record Preview(String url, OffsetDateTime expiresAt) {
    }

    public record DatasetDetail(
            DatasetView dataset,
            TaskBrief task,
            AnnotationSummary annotation,
            Preview preview
    ) {
    }

    public record ItemResult(long id, String status, String message) {
    }

    public record DatasetStats(
            long datasetTotal,
            BigDecimal datasetTotalDuration,
            long annotationTotal,
            long qualifiedAnnotationTotal,
            BigDecimal averageAnnotationsPerDataset,
            BigDecimal annotationTotalDuration,
            BigDecimal averageAnnotationDurationPerDataset,
            long invalidDatasetTotal,
            BigDecimal checkedQualifiedRate,
            long invalidCollect,
            long semanticUncorrected,
            long semanticCorrected
    ) {
    }

    public record OptionView(long id, String name) {
    }
}

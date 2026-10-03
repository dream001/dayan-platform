package com.dayan.platform.vo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public final class RobotViews {

    private RobotViews() {
    }

    public record RobotSummary(
            long id,
            String name,
            String iconUrl,
            Long iconFileId,
            String titleZh,
            String titleEn,
            String robotType,
            String actionMappingSupport,
            String description,
            String company,
            String introductionUrl,
            boolean builtIn,
            long datasetCount,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record RobotDataset(
            long id,
            String name,
            String dataType,
            long sizeBytes,
            BigDecimal durationSeconds,
            String uploaderName,
            OffsetDateTime uploadedAt,
            long annotationCount
    ) {
    }
}

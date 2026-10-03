package com.dayan.platform.vo;

import java.time.OffsetDateTime;
import java.util.List;

public final class SkillLibraryViews {

    private SkillLibraryViews() {
    }

    public record ProjectOption(long id, String code, String name) {
    }

    public record SkillSample(
            long annotationId,
            long datasetId,
            String description,
            String projectName,
            String dataType,
            String contentType,
            String mediaType,
            String previewUrl,
            OffsetDateTime previewExpiresAt,
            OffsetDateTime createdAt
    ) {
    }

    public record SkillSummary(
            String key,
            String name,
            String description,
            String category,
            String difficulty,
            String status,
            String currentVersion,
            String usageScene,
            List<String> tags,
            long annotationCount,
            long projectCount,
            long recentUsageCount,
            long versionCount,
            long dependencyCount,
            double qualityRate,
            List<SkillSample> samples
    ) {
    }

    public record SkillLibrary(
            long skillCount,
            long annotationCount,
            List<SkillSummary> skills
    ) {
    }
}

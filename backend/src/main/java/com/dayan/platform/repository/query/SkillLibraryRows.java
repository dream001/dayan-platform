package com.dayan.platform.repository.query;

import java.time.OffsetDateTime;

public final class SkillLibraryRows {

    private SkillLibraryRows() {
    }

    public static class SkillSummaryRow {
        public String skillKey;
        public String displayName;
        public String description;
        public String category;
        public String difficulty;
        public String status;
        public String currentVersion;
        public String usageScene;
        public String tagsCsv;
        public long annotationCount;
        public long projectCount;
        public long recentUsageCount;
        public long versionCount;
        public long dependencyCount;
        public double qualityRate;
    }

    public static class SkillSampleRow {
        public String skillKey;
        public long annotationId;
        public long datasetId;
        public long fileId;
        public String description;
        public String projectName;
        public String dataType;
        public String contentType;
        public String mediaType;
        public OffsetDateTime createdAt;
    }
}

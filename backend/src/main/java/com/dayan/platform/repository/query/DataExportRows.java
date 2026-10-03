package com.dayan.platform.repository.query;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public final class DataExportRows {

    private DataExportRows() {
    }

    public static class DatasetRow {
        public Long id;
        public String name;
        public String dataType;
        public Long sizeBytes;
        public BigDecimal durationSeconds;
        public Long projectId;
        public String projectName;
        public String collectorName;
        public OffsetDateTime createdAt;
        public String objectKey;
        public String originalName;
        public String contentType;
    }

    public static class AnnotationRow {
        public Long id;
        public Long datasetId;
        public Long taskId;
        public Long annotatorId;
        public String annotatorName;
        public String contentText;
        public BigDecimal coveredDurationSeconds;
        public Boolean isValid;
        public Boolean isQualified;
        public Boolean reviewed;
        public OffsetDateTime createdAt;
    }

    public static class TaskRow {
        public Long id;
        public String name;
        public String format;
        public String status;
        public Integer progress;
        public Integer processedCount;
        public Integer datasetCount;
        public String configJson;
        public String fileName;
        public String objectKey;
        public String contentType;
        public Long fileSize;
        public String errorMessage;
        public Long creatorId;
        public String creatorName;
        public OffsetDateTime startedAt;
        public OffsetDateTime completedAt;
        public OffsetDateTime createdAt;
    }

    public static class QuotaRow {
        public Long userId;
        public String username;
        public String displayName;
        public Integer used;
        public Integer quotaLimit;
    }
}

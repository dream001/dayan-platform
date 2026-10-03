package com.dayan.platform.repository.query;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public final class RobotRows {

    private RobotRows() {
    }

    public static class CatalogRow {
        public Long id;
        public String name;
        public String iconUrl;
        public String titleZh;
        public String titleEn;
        public String robotType;
        public String actionMappingSupport;
        public String description;
        public String company;
        public String introductionUrl;
        public Boolean builtIn;
        public Long datasetCount;
        public OffsetDateTime createdAt;
        public OffsetDateTime updatedAt;
    }

    public static class DatasetRow {
        public Long id;
        public String name;
        public String dataType;
        public Long sizeBytes;
        public BigDecimal durationSeconds;
        public String uploaderName;
        public OffsetDateTime uploadedAt;
        public Long annotationCount;
    }
}

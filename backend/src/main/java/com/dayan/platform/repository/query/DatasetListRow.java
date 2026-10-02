package com.dayan.platform.repository.query;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class DatasetListRow {

    public Long id;
    public String name;
    public Long fileId;
    public String dataType;
    public Long sizeBytes;
    public BigDecimal durationSeconds;
    public String annotationStatus;
    public Long projectId;
    public String projectName;
    public Long taskId;
    public String robotCode;
    public Long collectorId;
    public String collectorName;
    public String sourceTaskCode;
    public Long uploaderId;
    public String uploaderName;
    public String metadataStatus;
    public Boolean openShared;
    public OffsetDateTime createdAt;
    public OffsetDateTime updatedAt;
    public String tagNames;
}

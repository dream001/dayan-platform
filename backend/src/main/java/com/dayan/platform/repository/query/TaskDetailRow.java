package com.dayan.platform.repository.query;

import java.time.OffsetDateTime;

public class TaskDetailRow {

    public Long id;
    public String name;
    public String status;
    public Integer datasetCount;
    public Long creatorId;
    public String creatorName;
    public OffsetDateTime createdAt;
}

package com.dayan.platform.model;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

@TableName("data_collection_task")
public class CollectionTask extends BaseAuditedModel {

    private String name;
    private Long projectId;
    private Integer targetCount;
    private Integer averageDurationSeconds;
    private String notes;
    private String initialScene;
    private Boolean remoteOperationEnabled;
    private String status;
    private Long createdBy;
    private OffsetDateTime deletedAt;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Integer getTargetCount() {
        return targetCount;
    }

    public void setTargetCount(Integer targetCount) {
        this.targetCount = targetCount;
    }

    public Integer getAverageDurationSeconds() {
        return averageDurationSeconds;
    }

    public void setAverageDurationSeconds(Integer averageDurationSeconds) {
        this.averageDurationSeconds = averageDurationSeconds;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getInitialScene() {
        return initialScene;
    }

    public void setInitialScene(String initialScene) {
        this.initialScene = initialScene;
    }

    public Boolean getRemoteOperationEnabled() {
        return remoteOperationEnabled;
    }

    public void setRemoteOperationEnabled(Boolean remoteOperationEnabled) {
        this.remoteOperationEnabled = remoteOperationEnabled;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(OffsetDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
}

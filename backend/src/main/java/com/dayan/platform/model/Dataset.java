package com.dayan.platform.model;

import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@TableName("data_dataset")
public class Dataset extends BaseAuditedModel {

    private String name;
    private Long fileId;
    private String thumbnailObjectKey;
    private String dataType;
    private Long sizeBytes;
    private BigDecimal durationSeconds;
    private String annotationStatus;
    private Long projectId;
    private Long taskId;
    private String robotCode;
    private Long collectorId;
    private String sourceTaskCode;
    private Long uploaderId;
    private String metadataStatus;
    private Boolean openShared;
    private Boolean deleted;
    private OffsetDateTime deletedAt;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getFileId() {
        return fileId;
    }

    public void setFileId(Long fileId) {
        this.fileId = fileId;
    }

    public String getThumbnailObjectKey() {
        return thumbnailObjectKey;
    }

    public void setThumbnailObjectKey(String thumbnailObjectKey) {
        this.thumbnailObjectKey = thumbnailObjectKey;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public BigDecimal getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(BigDecimal durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public String getAnnotationStatus() {
        return annotationStatus;
    }

    public void setAnnotationStatus(String annotationStatus) {
        this.annotationStatus = annotationStatus;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getRobotCode() {
        return robotCode;
    }

    public void setRobotCode(String robotCode) {
        this.robotCode = robotCode;
    }

    public Long getCollectorId() {
        return collectorId;
    }

    public void setCollectorId(Long collectorId) {
        this.collectorId = collectorId;
    }

    public String getSourceTaskCode() {
        return sourceTaskCode;
    }

    public void setSourceTaskCode(String sourceTaskCode) {
        this.sourceTaskCode = sourceTaskCode;
    }

    public Long getUploaderId() {
        return uploaderId;
    }

    public void setUploaderId(Long uploaderId) {
        this.uploaderId = uploaderId;
    }

    public String getMetadataStatus() {
        return metadataStatus;
    }

    public void setMetadataStatus(String metadataStatus) {
        this.metadataStatus = metadataStatus;
    }

    public Boolean getOpenShared() {
        return openShared;
    }

    public void setOpenShared(Boolean openShared) {
        this.openShared = openShared;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(OffsetDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
}

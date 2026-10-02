package com.dayan.platform.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@TableName("data_annotation")
public class DatasetAnnotation {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long datasetId;
    private Long taskId;
    private Long annotatorId;
    private String contentText;
    private BigDecimal coveredDurationSeconds;
    private Boolean isValid;
    private Boolean isQualified;
    private Boolean reviewed;
    private Boolean invalidCollect;
    private Boolean semanticError;
    private Boolean semanticCorrected;
    private OffsetDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(Long datasetId) {
        this.datasetId = datasetId;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public Long getAnnotatorId() {
        return annotatorId;
    }

    public void setAnnotatorId(Long annotatorId) {
        this.annotatorId = annotatorId;
    }

    public String getContentText() {
        return contentText;
    }

    public void setContentText(String contentText) {
        this.contentText = contentText;
    }

    public BigDecimal getCoveredDurationSeconds() {
        return coveredDurationSeconds;
    }

    public void setCoveredDurationSeconds(BigDecimal coveredDurationSeconds) {
        this.coveredDurationSeconds = coveredDurationSeconds;
    }

    public Boolean getIsValid() {
        return isValid;
    }

    public void setIsValid(Boolean isValid) {
        this.isValid = isValid;
    }

    public Boolean getIsQualified() {
        return isQualified;
    }

    public void setIsQualified(Boolean isQualified) {
        this.isQualified = isQualified;
    }

    public Boolean getReviewed() {
        return reviewed;
    }

    public void setReviewed(Boolean reviewed) {
        this.reviewed = reviewed;
    }

    public Boolean getInvalidCollect() {
        return invalidCollect;
    }

    public void setInvalidCollect(Boolean invalidCollect) {
        this.invalidCollect = invalidCollect;
    }

    public Boolean getSemanticError() {
        return semanticError;
    }

    public void setSemanticError(Boolean semanticError) {
        this.semanticError = semanticError;
    }

    public Boolean getSemanticCorrected() {
        return semanticCorrected;
    }

    public void setSemanticCorrected(Boolean semanticCorrected) {
        this.semanticCorrected = semanticCorrected;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

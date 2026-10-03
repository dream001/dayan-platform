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
    private String skillName;
    private String skillNameZh;
    private String objectAName;
    private String objectANameZh;
    private String objectBName;
    private String objectBNameZh;
    private String actionName;
    private BigDecimal startOffsetSeconds;
    private BigDecimal endOffsetSeconds;
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

    public String getSkillName() {
        return skillName;
    }

    public void setSkillName(String skillName) {
        this.skillName = skillName;
    }

    public String getSkillNameZh() {
        return skillNameZh;
    }

    public void setSkillNameZh(String skillNameZh) {
        this.skillNameZh = skillNameZh;
    }

    public String getObjectAName() {
        return objectAName;
    }

    public void setObjectAName(String objectAName) {
        this.objectAName = objectAName;
    }

    public String getObjectANameZh() {
        return objectANameZh;
    }

    public void setObjectANameZh(String objectANameZh) {
        this.objectANameZh = objectANameZh;
    }

    public String getObjectBName() {
        return objectBName;
    }

    public void setObjectBName(String objectBName) {
        this.objectBName = objectBName;
    }

    public String getObjectBNameZh() {
        return objectBNameZh;
    }

    public void setObjectBNameZh(String objectBNameZh) {
        this.objectBNameZh = objectBNameZh;
    }

    public String getActionName() {
        return actionName;
    }

    public void setActionName(String actionName) {
        this.actionName = actionName;
    }

    public BigDecimal getStartOffsetSeconds() {
        return startOffsetSeconds;
    }

    public void setStartOffsetSeconds(BigDecimal startOffsetSeconds) {
        this.startOffsetSeconds = startOffsetSeconds;
    }

    public BigDecimal getEndOffsetSeconds() {
        return endOffsetSeconds;
    }

    public void setEndOffsetSeconds(BigDecimal endOffsetSeconds) {
        this.endOffsetSeconds = endOffsetSeconds;
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

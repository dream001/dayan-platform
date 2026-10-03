package com.dayan.platform.model;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

@TableName("data_qc_execution")
public class QualityExecution extends BaseAuditedModel {

    private Long ruleId;
    private Long datasetId;
    private String status;
    private Integer progress;
    private Boolean passed;
    private String reportJson;
    private String resultJson;
    private String errorMessage;
    private String triggerType;
    private Boolean overridePass;
    private String overrideReason;
    private Long overrideBy;
    private OffsetDateTime overrideAt;
    private Long createdBy;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;

    public Long getRuleId() {
        return ruleId;
    }

    public void setRuleId(Long ruleId) {
        this.ruleId = ruleId;
    }

    public Long getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(Long datasetId) {
        this.datasetId = datasetId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getProgress() {
        return progress;
    }

    public void setProgress(Integer progress) {
        this.progress = progress;
    }

    public Boolean getPassed() {
        return passed;
    }

    public void setPassed(Boolean passed) {
        this.passed = passed;
    }

    public String getReportJson() {
        return reportJson;
    }

    public void setReportJson(String reportJson) {
        this.reportJson = reportJson;
    }

    public String getResultJson() {
        return resultJson;
    }

    public void setResultJson(String resultJson) {
        this.resultJson = resultJson;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(String triggerType) {
        this.triggerType = triggerType;
    }

    public Boolean getOverridePass() {
        return overridePass;
    }

    public void setOverridePass(Boolean overridePass) {
        this.overridePass = overridePass;
    }

    public String getOverrideReason() {
        return overrideReason;
    }

    public void setOverrideReason(String overrideReason) {
        this.overrideReason = overrideReason;
    }

    public Long getOverrideBy() {
        return overrideBy;
    }

    public void setOverrideBy(Long overrideBy) {
        this.overrideBy = overrideBy;
    }

    public OffsetDateTime getOverrideAt() {
        return overrideAt;
    }

    public void setOverrideAt(OffsetDateTime overrideAt) {
        this.overrideAt = overrideAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(OffsetDateTime completedAt) {
        this.completedAt = completedAt;
    }
}

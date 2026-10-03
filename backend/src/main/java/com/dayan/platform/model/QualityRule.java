package com.dayan.platform.model;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("data_qc_rule")
public class QualityRule extends BaseAuditedModel {

    private String name;
    private String description;
    private String scope;
    private Long projectId;
    private String datasetPattern;
    private String algorithmCode;
    private Boolean enabled;
    private Integer priority;
    private String assertionsJson;
    private Long creatorId;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getDatasetPattern() {
        return datasetPattern;
    }

    public void setDatasetPattern(String datasetPattern) {
        this.datasetPattern = datasetPattern;
    }

    public String getAlgorithmCode() {
        return algorithmCode;
    }

    public void setAlgorithmCode(String algorithmCode) {
        this.algorithmCode = algorithmCode;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public String getAssertionsJson() {
        return assertionsJson;
    }

    public void setAssertionsJson(String assertionsJson) {
        this.assertionsJson = assertionsJson;
    }

    public Long getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(Long creatorId) {
        this.creatorId = creatorId;
    }
}

package com.dayan.platform.dto;

import java.math.BigDecimal;
import java.util.List;

public class DatasetFilter extends PageQuery {

    private String scope = "ALL";
    private Long projectId;
    private List<Long> projectIds;
    private String name;
    private String robotCode;
    private String tag;
    private Long uploaderId;
    private List<Long> collectorIds;
    private String sourceTaskCode;
    private BigDecimal minDuration;
    private BigDecimal maxDuration;
    private String annotationText;
    private String sortDir = "DESC";

    private long currentUserId;
    private boolean admin;
    private long offset;

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

    public List<Long> getProjectIds() {
        return projectIds;
    }

    public void setProjectIds(List<Long> projectIds) {
        this.projectIds = projectIds;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRobotCode() {
        return robotCode;
    }

    public void setRobotCode(String robotCode) {
        this.robotCode = robotCode;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public Long getUploaderId() {
        return uploaderId;
    }

    public void setUploaderId(Long uploaderId) {
        this.uploaderId = uploaderId;
    }

    public List<Long> getCollectorIds() {
        return collectorIds;
    }

    public void setCollectorIds(List<Long> collectorIds) {
        this.collectorIds = collectorIds;
    }

    public String getSourceTaskCode() {
        return sourceTaskCode;
    }

    public void setSourceTaskCode(String sourceTaskCode) {
        this.sourceTaskCode = sourceTaskCode;
    }

    public BigDecimal getMinDuration() {
        return minDuration;
    }

    public void setMinDuration(BigDecimal minDuration) {
        this.minDuration = minDuration;
    }

    public BigDecimal getMaxDuration() {
        return maxDuration;
    }

    public void setMaxDuration(BigDecimal maxDuration) {
        this.maxDuration = maxDuration;
    }

    public String getAnnotationText() {
        return annotationText;
    }

    public void setAnnotationText(String annotationText) {
        this.annotationText = annotationText;
    }

    public String getSortDir() {
        return sortDir;
    }

    public void setSortDir(String sortDir) {
        this.sortDir = sortDir;
    }

    public long getCurrentUserId() {
        return currentUserId;
    }

    public void setCurrentUserId(long currentUserId) {
        this.currentUserId = currentUserId;
    }

    public boolean isAdmin() {
        return admin;
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }

    public long getOffset() {
        return offset;
    }

    public void setOffset(long offset) {
        this.offset = offset;
    }
}

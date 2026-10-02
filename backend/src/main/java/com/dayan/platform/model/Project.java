package com.dayan.platform.model;

import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;

@TableName("basic_project")
public class Project extends BaseAuditedModel {

    private String code;
    private String name;
    private String description;
    private String projectType;
    private String accessLevel;
    private String status;
    private String storageProvider;
    private Long storageQuotaBytes;
    private LocalDate startDate;
    private LocalDate endDate;
    private String annotationGuideline;
    private BigDecimal qualityThreshold;
    private String reviewMode;
    private Boolean notificationEnabled;
    private Long ownerId;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

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

    public String getProjectType() {
        return projectType;
    }

    public void setProjectType(String projectType) {
        this.projectType = projectType;
    }

    public String getAccessLevel() {
        return accessLevel;
    }

    public void setAccessLevel(String accessLevel) {
        this.accessLevel = accessLevel;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStorageProvider() {
        return storageProvider;
    }

    public void setStorageProvider(String storageProvider) {
        this.storageProvider = storageProvider;
    }

    public Long getStorageQuotaBytes() {
        return storageQuotaBytes;
    }

    public void setStorageQuotaBytes(Long storageQuotaBytes) {
        this.storageQuotaBytes = storageQuotaBytes;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public String getAnnotationGuideline() {
        return annotationGuideline;
    }

    public void setAnnotationGuideline(String annotationGuideline) {
        this.annotationGuideline = annotationGuideline;
    }

    public BigDecimal getQualityThreshold() {
        return qualityThreshold;
    }

    public void setQualityThreshold(BigDecimal qualityThreshold) {
        this.qualityThreshold = qualityThreshold;
    }

    public String getReviewMode() {
        return reviewMode;
    }

    public void setReviewMode(String reviewMode) {
        this.reviewMode = reviewMode;
    }

    public Boolean getNotificationEnabled() {
        return notificationEnabled;
    }

    public void setNotificationEnabled(Boolean notificationEnabled) {
        this.notificationEnabled = notificationEnabled;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }
}

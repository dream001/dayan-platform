package com.dayan.platform.model;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

@TableName("basic_device")
public class Device extends BaseAuditedModel {

    private String agentId;
    private String agentToken;
    private String deviceCode;
    private String remark;
    private Long robotId;
    private Long projectId;
    private Long createdBy;
    private Long collectionTaskId;
    private OffsetDateTime lastReportAt;
    private String hostname;
    private String operatingSystem;
    private String platform;
    private String kernelVersion;
    private String ipAddresses;
    private Boolean deleted;
    private OffsetDateTime deletedAt;

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getAgentToken() { return agentToken; }
    public void setAgentToken(String agentToken) { this.agentToken = agentToken; }
    public String getDeviceCode() { return deviceCode; }
    public void setDeviceCode(String deviceCode) { this.deviceCode = deviceCode; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Long getRobotId() { return robotId; }
    public void setRobotId(Long robotId) { this.robotId = robotId; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Long getCollectionTaskId() { return collectionTaskId; }
    public void setCollectionTaskId(Long collectionTaskId) { this.collectionTaskId = collectionTaskId; }
    public OffsetDateTime getLastReportAt() { return lastReportAt; }
    public void setLastReportAt(OffsetDateTime lastReportAt) { this.lastReportAt = lastReportAt; }
    public String getHostname() { return hostname; }
    public void setHostname(String hostname) { this.hostname = hostname; }
    public String getOperatingSystem() { return operatingSystem; }
    public void setOperatingSystem(String operatingSystem) { this.operatingSystem = operatingSystem; }
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public String getKernelVersion() { return kernelVersion; }
    public void setKernelVersion(String kernelVersion) { this.kernelVersion = kernelVersion; }
    public String getIpAddresses() { return ipAddresses; }
    public void setIpAddresses(String ipAddresses) { this.ipAddresses = ipAddresses; }
    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }
    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(OffsetDateTime deletedAt) { this.deletedAt = deletedAt; }
}

package com.dayan.platform.model;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

@TableName("cloud_storage")
public class CloudStorage extends BaseAuditedModel {

    private String storageKey;
    private String name;
    private String provider;
    private String endpoint;
    private String region;
    private String bucket;
    private String accessKeyCiphertext;
    private String secretKeyCiphertext;
    private Boolean defaultStorage;
    private Boolean enabled;
    private String status;
    private String lastCheckMessage;
    private Long lastCheckLatencyMs;
    private Long usageBytes;
    private Long objectCount;
    private OffsetDateTime lastCheckedAt;
    private Long createdBy;

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getAccessKeyCiphertext() {
        return accessKeyCiphertext;
    }

    public void setAccessKeyCiphertext(String accessKeyCiphertext) {
        this.accessKeyCiphertext = accessKeyCiphertext;
    }

    public String getSecretKeyCiphertext() {
        return secretKeyCiphertext;
    }

    public void setSecretKeyCiphertext(String secretKeyCiphertext) {
        this.secretKeyCiphertext = secretKeyCiphertext;
    }

    public Boolean getDefaultStorage() {
        return defaultStorage;
    }

    public void setDefaultStorage(Boolean defaultStorage) {
        this.defaultStorage = defaultStorage;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLastCheckMessage() {
        return lastCheckMessage;
    }

    public void setLastCheckMessage(String lastCheckMessage) {
        this.lastCheckMessage = lastCheckMessage;
    }

    public Long getLastCheckLatencyMs() {
        return lastCheckLatencyMs;
    }

    public void setLastCheckLatencyMs(Long lastCheckLatencyMs) {
        this.lastCheckLatencyMs = lastCheckLatencyMs;
    }

    public Long getUsageBytes() {
        return usageBytes;
    }

    public void setUsageBytes(Long usageBytes) {
        this.usageBytes = usageBytes;
    }

    public Long getObjectCount() {
        return objectCount;
    }

    public void setObjectCount(Long objectCount) {
        this.objectCount = objectCount;
    }

    public OffsetDateTime getLastCheckedAt() {
        return lastCheckedAt;
    }

    public void setLastCheckedAt(OffsetDateTime lastCheckedAt) {
        this.lastCheckedAt = lastCheckedAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }
}

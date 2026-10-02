package com.dayan.platform.model;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

@TableName("ai_model")
public class AiModel extends BaseAuditedModel {

    private String manufacturer;
    private String name;
    private String accessAddress;
    private String modelUrl;
    private String modelType;
    private String accessKeyCiphertext;
    private String secretKeyCiphertext;
    private Boolean enabled;
    private String lastTestStatus;
    private String lastTestMessage;
    private Long lastTestLatencyMs;
    private OffsetDateTime lastTestedAt;

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAccessAddress() {
        return accessAddress;
    }

    public void setAccessAddress(String accessAddress) {
        this.accessAddress = accessAddress;
    }

    public String getModelUrl() {
        return modelUrl;
    }

    public void setModelUrl(String modelUrl) {
        this.modelUrl = modelUrl;
    }

    public String getModelType() {
        return modelType;
    }

    public void setModelType(String modelType) {
        this.modelType = modelType;
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

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getLastTestStatus() {
        return lastTestStatus;
    }

    public void setLastTestStatus(String lastTestStatus) {
        this.lastTestStatus = lastTestStatus;
    }

    public String getLastTestMessage() {
        return lastTestMessage;
    }

    public void setLastTestMessage(String lastTestMessage) {
        this.lastTestMessage = lastTestMessage;
    }

    public Long getLastTestLatencyMs() {
        return lastTestLatencyMs;
    }

    public void setLastTestLatencyMs(Long lastTestLatencyMs) {
        this.lastTestLatencyMs = lastTestLatencyMs;
    }

    public OffsetDateTime getLastTestedAt() {
        return lastTestedAt;
    }

    public void setLastTestedAt(OffsetDateTime lastTestedAt) {
        this.lastTestedAt = lastTestedAt;
    }
}

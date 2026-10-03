package com.dayan.platform.model;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

@TableName("mqtt_connection")
public class MqttConnection extends BaseAuditedModel {

    private String name;
    private String clientId;
    private String brokerUrl;
    private String usernameCiphertext;
    private String passwordCiphertext;
    private Boolean tlsEnabled;
    private Boolean cleanSession;
    private Integer keepAliveSeconds;
    private Integer connectionTimeoutSeconds;
    private Boolean enabled;
    private String status;
    private String lastCheckMessage;
    private Long lastCheckLatencyMs;
    private OffsetDateTime lastCheckedAt;
    private Long createdBy;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getBrokerUrl() { return brokerUrl; }
    public void setBrokerUrl(String brokerUrl) { this.brokerUrl = brokerUrl; }
    public String getUsernameCiphertext() { return usernameCiphertext; }
    public void setUsernameCiphertext(String usernameCiphertext) { this.usernameCiphertext = usernameCiphertext; }
    public String getPasswordCiphertext() { return passwordCiphertext; }
    public void setPasswordCiphertext(String passwordCiphertext) { this.passwordCiphertext = passwordCiphertext; }
    public Boolean getTlsEnabled() { return tlsEnabled; }
    public void setTlsEnabled(Boolean tlsEnabled) { this.tlsEnabled = tlsEnabled; }
    public Boolean getCleanSession() { return cleanSession; }
    public void setCleanSession(Boolean cleanSession) { this.cleanSession = cleanSession; }
    public Integer getKeepAliveSeconds() { return keepAliveSeconds; }
    public void setKeepAliveSeconds(Integer keepAliveSeconds) { this.keepAliveSeconds = keepAliveSeconds; }
    public Integer getConnectionTimeoutSeconds() { return connectionTimeoutSeconds; }
    public void setConnectionTimeoutSeconds(Integer connectionTimeoutSeconds) {
        this.connectionTimeoutSeconds = connectionTimeoutSeconds;
    }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getLastCheckMessage() { return lastCheckMessage; }
    public void setLastCheckMessage(String lastCheckMessage) { this.lastCheckMessage = lastCheckMessage; }
    public Long getLastCheckLatencyMs() { return lastCheckLatencyMs; }
    public void setLastCheckLatencyMs(Long lastCheckLatencyMs) { this.lastCheckLatencyMs = lastCheckLatencyMs; }
    public OffsetDateTime getLastCheckedAt() { return lastCheckedAt; }
    public void setLastCheckedAt(OffsetDateTime lastCheckedAt) { this.lastCheckedAt = lastCheckedAt; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
}

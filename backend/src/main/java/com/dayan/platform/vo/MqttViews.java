package com.dayan.platform.vo;

import java.time.OffsetDateTime;

public final class MqttViews {

    private MqttViews() {
    }

    public record ConnectionView(
            long id,
            String name,
            String clientId,
            String brokerUrl,
            String usernameHint,
            boolean credentialConfigured,
            boolean tlsEnabled,
            boolean cleanSession,
            int keepAliveSeconds,
            int connectionTimeoutSeconds,
            boolean enabled,
            String status,
            String lastCheckMessage,
            Long lastCheckLatencyMs,
            OffsetDateTime lastCheckedAt,
            long subscriptionCount,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record SubscriptionView(
            long id,
            long connectionId,
            String connectionName,
            String topicFilter,
            int qos,
            String description,
            boolean enabled,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record Overview(
            long connectionCount,
            long enabledConnectionCount,
            long availableConnectionCount,
            long subscriptionCount,
            long enabledSubscriptionCount
    ) {
    }
}

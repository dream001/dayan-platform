package com.dayan.platform.vo;

import java.time.OffsetDateTime;
import java.util.Map;

public final class CloudStorageViews {

    private CloudStorageViews() {
    }

    public record CloudStorageView(
            long id,
            String storageKey,
            String name,
            String provider,
            String endpoint,
            String region,
            String bucket,
            String accessKeyHint,
            boolean credentialConfigured,
            boolean defaultStorage,
            boolean enabled,
            String status,
            String lastCheckMessage,
            Long lastCheckLatencyMs,
            long usageBytes,
            long objectCount,
            OffsetDateTime lastCheckedAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record CloudStorageOverview(
            long total,
            long enabled,
            long available,
            long usageBytes,
            long objectCount,
            Map<String, Long> providerCounts
    ) {
    }
}

package com.dayan.platform.vo;

import java.time.OffsetDateTime;

public final class AiModelViews {

    private AiModelViews() {
    }

    public record ModelSummary(
            long id,
            String manufacturer,
            String name,
            String accessAddress,
            String modelUrl,
            String modelType,
            boolean accessKeyConfigured,
            boolean secretKeyConfigured,
            boolean enabled,
            String lastTestStatus,
            String lastTestMessage,
            Long lastTestLatencyMs,
            OffsetDateTime lastTestedAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record ModelTestResult(
            boolean success,
            String message,
            long latencyMs,
            OffsetDateTime testedAt
    ) {
    }
}

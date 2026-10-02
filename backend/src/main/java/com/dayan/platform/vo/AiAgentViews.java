package com.dayan.platform.vo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public final class AiAgentViews {

    private AiAgentViews() {
    }

    public record AgentView(
            long id,
            String name,
            String description,
            String systemPrompt,
            long modelId,
            String modelName,
            String modelManufacturer,
            String modelType,
            BigDecimal temperature,
            int maxTokens,
            boolean enabled,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record AgentDebugResult(
            String content,
            String modelName,
            long latencyMs,
            OffsetDateTime completedAt
    ) {
    }
}

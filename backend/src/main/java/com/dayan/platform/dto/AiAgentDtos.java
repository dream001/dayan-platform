package com.dayan.platform.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public final class AiAgentDtos {

    private AiAgentDtos() {
    }

    public record AgentRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String description,
            @NotBlank @Size(max = 20000) String systemPrompt,
            @NotNull @Min(1) Long modelId,
            @NotNull @DecimalMin("0.0") @DecimalMax("2.0") BigDecimal temperature,
            @NotNull @Min(1) @Max(32768) Integer maxTokens,
            boolean enabled
    ) {
    }

    public record AgentStatusRequest(
            boolean enabled
    ) {
    }

    public record AgentDebugRequest(
            @NotBlank @Size(max = 20000) String message
    ) {
    }
}

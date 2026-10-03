package com.dayan.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class AiModelDtos {

    private AiModelDtos() {
    }

    public enum ModelType {
        CHAT,
        EMBEDDING,
        MULTIMODAL,
        RERANK,
        IMAGE,
        VIDEO,
        AUDIO
    }

    public record ModelRequest(
            @NotBlank @Size(max = 100) String manufacturer,
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Size(max = 500) String accessAddress,
            @NotBlank @Size(max = 1000) String modelUrl,
            @NotNull ModelType modelType,
            @Size(max = 4096) String accessKey,
            @Size(max = 4096) String secretKey,
            boolean enabled
    ) {
    }

    public record ModelStatusRequest(
            boolean enabled
    ) {
    }

    public record ModelDebugRequest(
            @NotBlank @Size(max = 4000) String input
    ) {
    }
}

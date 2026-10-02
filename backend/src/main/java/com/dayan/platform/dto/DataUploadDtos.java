package com.dayan.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class DataUploadDtos {

    private DataUploadDtos() {
    }

    public record CreateSessionRequest(
            @NotNull @Positive Long projectId,
            @NotBlank @Size(max = 64) String storageKey,
            @NotBlank @Size(max = 32) String dataType,
            @NotBlank @Size(max = 255) String fileName,
            @NotBlank @Size(max = 255) String contentType,
            @NotNull @Positive Long totalSize,
            @NotBlank @Size(max = 128) String sourceFingerprint,
            @Size(max = 100) String robotType
    ) {
    }
}

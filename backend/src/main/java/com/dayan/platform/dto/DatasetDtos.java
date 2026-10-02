package com.dayan.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;

public final class DatasetDtos {

    private DatasetDtos() {
    }

    public record DatasetRegisterRequest(
            @NotNull @Positive Long fileId,
            @NotBlank @Size(max = 255) String name,
            @Pattern(regexp = "VIDEO|AUDIO|MCAP") String dataType,
            @Positive Long projectId,
            @Size(max = 128) String robotCode,
            @Positive Long collectorId,
            @Size(max = 128) String sourceTaskCode,
            BigDecimal durationSeconds,
            List<@Size(max = 64) String> tags,
            Boolean openShared,
            Boolean restoreExisting
    ) {
    }

    public record RenameItem(
            @NotNull @Positive Long id,
            @NotBlank @Size(max = 255) String name
    ) {
    }

    public record RenameRequest(@NotEmpty @Valid List<RenameItem> items) {
    }

    public record BatchIds(@NotEmpty List<@NotNull @Positive Long> ids) {
    }

    public record AnnotateRequest(
            @NotEmpty List<@NotNull @Positive Long> ids,
            @NotBlank @Size(max = 255) String name
    ) {
    }

    public record TagBatchRequest(
            @NotEmpty List<@NotNull @Positive Long> ids,
            @NotEmpty List<@NotBlank @Size(max = 64) String> tags
    ) {
    }

    public record ImportRequest(
            @NotEmpty List<@NotNull @Positive Long> ids,
            @NotNull @Positive Long projectId
    ) {
    }

    public record RobotRequest(
            @NotEmpty List<@NotNull @Positive Long> ids,
            @Size(max = 128) String robotCode
    ) {
    }
}

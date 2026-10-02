package com.dayan.platform.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;

public final class CollectionTaskDtos {

    private CollectionTaskDtos() {
    }

    public record CollectionTaskRequest(
            @NotBlank @Size(max = 120) String name,
            @NotNull @Positive Long projectId,
            @NotEmpty Set<@Positive Long> assigneeIds,
            @NotNull @Positive Integer targetCount,
            @NotNull @Positive Integer averageDurationSeconds,
            @Size(max = 2000) String notes,
            @Size(max = 2000) String initialScene,
            @NotNull Boolean remoteOperationEnabled,
            @Valid @Size(max = 100) List<CollectionStepRequest> steps
    ) {
    }

    public record CollectionStepRequest(
            @Size(max = 200) String actionName,
            @Size(max = 200) String objectName,
            @Size(max = 200) String targetName,
            @Size(max = 500) String notes
    ) {
    }

    public record CollectionStatusRequest(
            @NotBlank
            @Pattern(regexp = "PENDING|WORKING|REVIEW_PENDING|REJECTED|APPROVED|SUBMITTED")
            String status
    ) {
    }
}

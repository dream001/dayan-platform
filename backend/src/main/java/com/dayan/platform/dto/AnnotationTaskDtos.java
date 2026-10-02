package com.dayan.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;

public final class AnnotationTaskDtos {

    private AnnotationTaskDtos() {
    }

    public record CreateRequest(
            @Size(max = 120) String name,
            @NotNull @Positive Long projectId,
            @NotEmpty Set<@Positive Long> annotatorIds,
            List<@Positive Long> reviewerIds,
            @NotEmpty Set<@Positive Long> datasetIds,
            @NotNull Boolean randomOrder
    ) {
    }

    public record UpdateRequest(
            @NotBlank @Size(max = 120) String name,
            @NotNull @Positive Long projectId,
            @NotNull @Positive Long annotatorId,
            @Positive Long reviewerId
    ) {
    }

    public record StatusRequest(
            @NotBlank
            @Pattern(regexp = "PENDING|WORKING|REVIEW_PENDING|REJECTED|APPROVED|SUBMITTED")
            String status,
            @Size(max = 1000) String rejectionReason
    ) {
    }

    public record DatasetReviewRequest(
            @Pattern(regexp = "VALID|INVALID") String result,
            @Size(max = 1000) String rejectionReason
    ) {
    }

    public record BatchAnnotationRequest(
            @NotBlank @Pattern(regexp = "QUICK|COPY|REPLACE") String mode,
            @Size(max = 10000) String description,
            @Positive Long sourceRelationId,
            @Size(max = 500) String findText,
            @Size(max = 500) String replaceText
    ) {
    }
}

package com.dayan.platform.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public final class ProjectDtos {

    private ProjectDtos() {
    }

    public record ProjectRequest(
            @NotBlank @Size(max = 64)
            @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_-]*$", message = "project code format is invalid")
            String code,
            @NotBlank @Size(max = 120) String name,
            @Size(max = 1000) String description,
            @NotBlank @Pattern(regexp = "PERSONAL|TEAM|SHARED") String projectType,
            @NotBlank @Pattern(regexp = "PUBLIC|PRIVATE|RESTRICTED") String accessLevel,
            @NotBlank @Size(max = 32) String storageProvider,
            @NotNull @Positive Long storageQuotaBytes,
            LocalDate startDate,
            LocalDate endDate,
            @Size(max = 10000) String annotationGuideline,
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal qualityThreshold,
            @NotBlank @Pattern(regexp = "NONE|SINGLE_REVIEW|DOUBLE_REVIEW") String reviewMode,
            @NotNull Boolean notificationEnabled
    ) {
    }

    public record ProjectStatusRequest(
            @NotBlank @Pattern(regexp = "PLANNING|ACTIVE|SUSPENDED|COMPLETED|ARCHIVED")
            String status
    ) {
    }

    public record ProjectMemberRequest(
            @NotNull @Positive Long userId,
            @NotBlank
            @Pattern(regexp = "PROJECT_ADMIN|PROJECT_MANAGER|ANNOTATOR|REVIEWER|OBSERVER")
            String role,
            @NotBlank @Pattern(regexp = "READ_ONLY|READ_WRITE|FULL")
            String dataAccessLevel,
            OffsetDateTime validFrom,
            OffsetDateTime validUntil
    ) {
    }
}

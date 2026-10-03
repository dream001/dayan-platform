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
            @NotBlank(message = "project code is required")
            @Size(max = 64, message = "project code must not exceed 64 characters")
            @Pattern(
                    regexp = "^[A-Za-z][A-Za-z0-9_-]*$",
                    message = "project code must start with a letter and contain only letters, numbers, underscores, or hyphens"
            )
            String code,
            @NotBlank(message = "project name is required")
            @Size(max = 120, message = "project name must not exceed 120 characters")
            String name,
            @Size(max = 1000, message = "project description must not exceed 1000 characters")
            String description,
            @NotBlank(message = "project type is required")
            @Pattern(regexp = "PERSONAL|TEAM|SHARED", message = "project type is invalid")
            String projectType,
            @NotBlank(message = "access level is required")
            @Pattern(regexp = "PUBLIC|PRIVATE|RESTRICTED", message = "access level is invalid")
            String accessLevel,
            @NotBlank(message = "storage provider is required")
            @Size(max = 32, message = "storage provider must not exceed 32 characters")
            String storageProvider,
            @NotNull(message = "storage quota is required")
            @Positive(message = "storage quota must be greater than zero")
            Long storageQuotaBytes,
            LocalDate startDate,
            LocalDate endDate,
            @Size(max = 10000, message = "annotation guideline must not exceed 10000 characters")
            String annotationGuideline,
            @NotNull(message = "quality threshold is required")
            @DecimalMin(value = "0.00", message = "quality threshold must be at least 0")
            @DecimalMax(value = "100.00", message = "quality threshold must not exceed 100")
            BigDecimal qualityThreshold,
            @NotBlank(message = "review mode is required")
            @Pattern(regexp = "NONE|SINGLE_REVIEW|DOUBLE_REVIEW", message = "review mode is invalid")
            String reviewMode,
            @NotNull(message = "notification setting is required")
            Boolean notificationEnabled
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

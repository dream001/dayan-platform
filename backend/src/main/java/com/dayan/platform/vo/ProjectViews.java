package com.dayan.platform.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public final class ProjectViews {

    private ProjectViews() {
    }

    public record ProjectSummary(
            long id,
            String code,
            String name,
            String description,
            String projectType,
            String accessLevel,
            String status,
            long storageQuotaBytes,
            LocalDate startDate,
            LocalDate endDate,
            long memberCount,
            String currentRole,
            boolean canEdit,
            boolean canManageMembers,
            boolean canDelete,
            OffsetDateTime updatedAt
    ) {
    }

    public record ProjectDetail(
            ProjectSummary summary,
            String storageProvider,
            String annotationGuideline,
            BigDecimal qualityThreshold,
            String reviewMode,
            boolean notificationEnabled,
            long ownerId,
            String ownerName,
            OffsetDateTime createdAt,
            ProjectMetrics metrics
    ) {
    }

    public record ProjectMetrics(
            long datasetCount,
            long videoCount,
            long audioCount,
            long mcapCount,
            long storageUsedBytes,
            long annotationTaskCount,
            long collectionTaskCount,
            long completedTaskCount,
            BigDecimal taskCompletionRate,
            BigDecimal qualityRate,
            long activeMemberCount
    ) {
    }

    public record ProjectMember(
            long userId,
            String username,
            String displayName,
            String role,
            String dataAccessLevel,
            OffsetDateTime validFrom,
            OffsetDateTime validUntil,
            boolean active
    ) {
    }

    public record ProjectUserOption(
            long id,
            String username,
            String displayName,
            String personnelType
    ) {
    }

    public record ProjectOverview(long total, long active, long planning, long archived) {
    }
}

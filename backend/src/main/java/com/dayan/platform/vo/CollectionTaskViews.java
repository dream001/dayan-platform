package com.dayan.platform.vo;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class CollectionTaskViews {

    private CollectionTaskViews() {
    }

    public record CollectionTaskSummary(
            long id,
            String name,
            long projectId,
            String projectName,
            int targetCount,
            int averageDurationSeconds,
            long collectedCount,
            String latestFileName,
            OffsetDateTime latestFileAt,
            List<String> assignees,
            List<String> actions,
            String status,
            long createdBy,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record CollectionTaskDetail(
            CollectionTaskSummary summary,
            String notes,
            String initialScene,
            boolean remoteOperationEnabled,
            List<CollectionAssignee> assignees,
            List<CollectionStep> steps,
            List<CollectionDataset> datasets,
            List<String> allowedTransitions,
            boolean canEdit,
            boolean canDelete,
            boolean canUnlinkData
    ) {
    }

    public record CollectionAssignee(long id, String username, String displayName) {
    }

    public record CollectionStep(
            long id,
            int sequenceNo,
            String actionName,
            String objectName,
            String targetName,
            String notes
    ) {
    }

    public record CollectionDataset(
            long id,
            String name,
            long sizeBytes,
            String status,
            OffsetDateTime uploadedAt
    ) {
    }

    public record CollectionProjectOption(long id, String name) {
    }

    public record CollectionOptions(
            List<CollectionProjectOption> projects,
            List<CollectionAssignee> collectors
    ) {
    }

    public record CollectionStatusCounts(long total, Map<String, Long> statuses) {
    }
}

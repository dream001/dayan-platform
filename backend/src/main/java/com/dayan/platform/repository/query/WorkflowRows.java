package com.dayan.platform.repository.query;

import java.time.OffsetDateTime;

public final class WorkflowRows {

    private WorkflowRows() {
    }

    public record MatchRuleRow(
            long id,
            String name,
            String description,
            String scope,
            Long projectId,
            String projectName,
            int priority,
            boolean enabled,
            String logicOperator,
            String conditionsJson,
            long creatorId,
            OffsetDateTime updatedAt
    ) {
    }

    public record ActionRuleRow(
            long id,
            String name,
            String description,
            String scope,
            Long projectId,
            String projectName,
            boolean enabled,
            String stepsJson,
            long creatorId,
            OffsetDateTime updatedAt
    ) {
    }

    public record DefinitionRow(
            long id,
            String name,
            String description,
            String scope,
            Long projectId,
            String projectName,
            int priority,
            boolean enabled,
            long matchRuleId,
            String matchRuleName,
            long actionRuleId,
            String actionRuleName,
            String stepsJson,
            long creatorId,
            OffsetDateTime updatedAt
    ) {
    }

    public record RunRow(
            long id,
            long workflowId,
            String workflowName,
            long datasetId,
            String datasetName,
            Long projectId,
            String projectName,
            String stage,
            String status,
            int progress,
            String triggerType,
            String stepsSnapshotJson,
            String errorMessage,
            OffsetDateTime startedAt,
            OffsetDateTime completedAt,
            OffsetDateTime createdAt
    ) {
    }

    public record DatasetRow(
            long id,
            String name,
            String originalName,
            String dataType,
            long sizeBytes,
            String robotCode,
            String objectKey,
            Long projectId,
            String projectName
    ) {
    }

    public record OverviewRow(
            long workflowCount,
            long enabledWorkflowCount,
            long matchRuleCount,
            long actionRuleCount,
            long queuedRunCount,
            long failedRunCount
    ) {
    }
}

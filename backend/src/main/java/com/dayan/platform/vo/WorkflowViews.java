package com.dayan.platform.vo;

import com.dayan.platform.dto.WorkflowDtos.ActionStep;
import com.dayan.platform.dto.WorkflowDtos.MatchCondition;
import java.time.OffsetDateTime;
import java.util.List;

public final class WorkflowViews {

    private WorkflowViews() {
    }

    public record MatchRuleView(
            long id,
            String name,
            String description,
            String scope,
            Long projectId,
            String projectName,
            int priority,
            boolean enabled,
            String logicOperator,
            List<MatchCondition> conditions,
            boolean canEdit,
            OffsetDateTime updatedAt
    ) {
    }

    public record ActionRuleView(
            long id,
            String name,
            String description,
            String scope,
            Long projectId,
            String projectName,
            boolean enabled,
            List<ActionStep> steps,
            boolean canEdit,
            OffsetDateTime updatedAt
    ) {
    }

    public record DefinitionView(
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
            int stepCount,
            boolean canEdit,
            OffsetDateTime updatedAt
    ) {
    }

    public record RunView(
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
            List<ActionStep> steps,
            String errorMessage,
            OffsetDateTime startedAt,
            OffsetDateTime completedAt,
            OffsetDateTime createdAt
    ) {
    }

    public record ProjectOption(long id, String name, boolean manageable) {
    }

    public record DatasetOption(
            long id,
            String name,
            String dataType,
            Long projectId,
            String projectName
    ) {
    }

    public record Overview(
            long workflowCount,
            long enabledWorkflowCount,
            long matchRuleCount,
            long actionRuleCount,
            long queuedRunCount,
            long failedRunCount,
            List<DefinitionView> workflows
    ) {
    }

    public record MatchSample(
            long id,
            String name,
            String dataType,
            String projectName
    ) {
    }

    public record TestResult(long scanned, long matched, List<MatchSample> samples) {
    }
}

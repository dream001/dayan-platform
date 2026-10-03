package com.dayan.platform.vo;

import com.dayan.platform.dto.QualityControlDtos.AssertionRequest;
import com.dayan.platform.dto.QualityControlDtos.ReportRequest;
import java.time.OffsetDateTime;
import java.util.List;

public final class QualityControlViews {

    private QualityControlViews() {
    }

    public record RuleView(
            long id,
            String name,
            String description,
            String scope,
            Long projectId,
            String projectName,
            String datasetPattern,
            String algorithmCode,
            boolean enabled,
            int priority,
            List<AssertionRequest> assertions,
            long creatorId,
            String creatorName,
            boolean canEdit,
            boolean canDelete,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record AssertionResult(
            AssertionRequest assertion,
            boolean passed,
            String actualValue,
            String message
    ) {
    }

    public record ExecutionView(
            long id,
            long ruleId,
            String ruleName,
            String ruleScope,
            long datasetId,
            String datasetName,
            Long projectId,
            String projectName,
            String dataType,
            String status,
            int progress,
            Boolean passed,
            Boolean effectivePass,
            List<AssertionResult> results,
            String errorMessage,
            String triggerType,
            Boolean overridePass,
            String overrideReason,
            String overrideByName,
            OffsetDateTime overrideAt,
            OffsetDateTime startedAt,
            OffsetDateTime completedAt,
            OffsetDateTime createdAt
    ) {
    }

    public record ExecutionDetail(ExecutionView execution, ReportRequest report) {
    }

    public record DatasetOption(long id, String name, String projectName) {
    }

    public record ProjectOption(long id, String name) {
    }

    public record Overview(
            long totalRules,
            long enabledRules,
            long queued,
            long passed,
            long failed,
            long overridden,
            boolean canManageGlobal
    ) {
    }

    public record RunResult(int queued, int skipped) {
    }
}

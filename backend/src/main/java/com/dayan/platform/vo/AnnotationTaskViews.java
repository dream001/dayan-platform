package com.dayan.platform.vo;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class AnnotationTaskViews {

    private AnnotationTaskViews() {
    }

    public record TaskSummary(
            long id,
            String name,
            long projectId,
            String projectName,
            long annotatorId,
            String annotatorName,
            Long reviewerId,
            String reviewerName,
            long creatorId,
            String creatorName,
            String status,
            String rejectionReason,
            long datasetCount,
            long reviewedCount,
            int progress,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record TaskDetail(
            TaskSummary summary,
            List<DatasetItem> datasets,
            List<String> allowedTransitions,
            boolean canEdit,
            boolean canDelete,
            boolean canExecute,
            boolean canReview
    ) {
    }

    public record DatasetItem(
            long relationId,
            long datasetId,
            String name,
            String dataType,
            long sizeBytes,
            String annotationDescription,
            String checkResult,
            String rejectionReason,
            OffsetDateTime createdAt
    ) {
    }

    public record PersonOption(long id, String username, String displayName) {
    }

    public record ProjectOption(long id, String name) {
    }

    public record DatasetOption(
            long id,
            long projectId,
            String name,
            String dataType,
            long sizeBytes,
            OffsetDateTime createdAt
    ) {
    }

    public record TaskOptions(
            List<ProjectOption> projects,
            List<PersonOption> annotators,
            List<PersonOption> reviewers,
            List<DatasetOption> datasets
    ) {
    }

    public record StatusCounts(long total, Map<String, Long> statuses) {
    }
}

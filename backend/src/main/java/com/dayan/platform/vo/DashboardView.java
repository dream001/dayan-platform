package com.dayan.platform.vo;

import com.dayan.platform.vo.AuditViews.OperationLogSummary;
import java.time.OffsetDateTime;
import java.util.List;

public record DashboardView(
        long totalUsers,
        long enabledUsers,
        long totalFiles,
        long totalFileSizeBytes,
        long recentOperationCount,
        List<OperationLogSummary> recentOperations,
        OffsetDateTime generatedAt
) {
    public DashboardView {
        recentOperations = List.copyOf(recentOperations);
    }
}

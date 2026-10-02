package com.dayan.platform.vo;

import java.time.OffsetDateTime;

public final class AuditViews {

    private AuditViews() {
    }

    public record OperationLogSummary(
            long id,
            Long operatorId,
            String operatorName,
            String module,
            String action,
            String targetType,
            String targetId,
            String result,
            String errorSummary,
            String requestId,
            OffsetDateTime occurredAt,
            Long durationMs
    ) {
    }

    public record OperationLogDetail(
            long id,
            Long operatorId,
            String operatorName,
            String module,
            String action,
            String targetType,
            String targetId,
            String result,
            String errorSummary,
            String ipAddress,
            String userAgent,
            String requestId,
            String details,
            OffsetDateTime occurredAt,
            Long durationMs
    ) {
    }
}

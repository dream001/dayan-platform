package com.dayan.platform.vo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class MonitoringViews {

    private MonitoringViews() {
    }

    public record MetricPoint(
            long databaseLatencyMs,
            Long redisLatencyMs,
            int queueBacklog,
            BigDecimal cpuUsagePercent,
            BigDecimal memoryUsagePercent,
            OffsetDateTime collectedAt
    ) {
    }

    public record Overview(
            MetricPoint latest,
            List<MetricPoint> trend,
            List<ComponentStatus> components,
            String range,
            OffsetDateTime generatedAt
    ) {
    }

    public record ComponentStatus(
            String key,
            String status,
            Long latencyMs
    ) {
    }

    public record ServiceStatus(
            String name,
            String status,
            Long latencyMs,
            String detail
    ) {
    }

    public record SystemInformation(
            String applicationName,
            String environment,
            String version,
            String hostname,
            String operatingSystem,
            int cpuCores,
            long totalMemoryBytes,
            long usedMemoryBytes,
            long uptimeSeconds,
            OffsetDateTime startedAt,
            List<ServiceStatus> services,
            OffsetDateTime generatedAt
    ) {
    }

    public record AccessLog(
            long id,
            Long userId,
            String username,
            String method,
            String requestPath,
            int statusCode,
            long durationMs,
            String ipAddress,
            String userAgent,
            String requestId,
            OffsetDateTime occurredAt
    ) {
    }

    public record OnlineUser(
            long sessionId,
            long userId,
            String username,
            String displayName,
            List<String> roles,
            OffsetDateTime activeAt,
            long sessionDurationSeconds,
            String currentPath,
            String ipAddress
    ) {
    }

    public record OnlineUsers(
            long onlineCount,
            long todayActiveCount,
            List<OnlineUser> users,
            OffsetDateTime generatedAt
    ) {
    }

    public record LoginLog(
            long id,
            String username,
            String result,
            String errorSummary,
            String ipAddress,
            OffsetDateTime occurredAt
    ) {
    }

    public record ExportTask(
            long id,
            String name,
            String format,
            String status,
            int progress,
            int processedCount,
            int datasetCount,
            String fileName,
            Long fileSize,
            String errorMessage,
            long creatorId,
            String creatorName,
            OffsetDateTime startedAt,
            OffsetDateTime completedAt,
            OffsetDateTime createdAt
    ) {
    }

    public record QueueSummary(
            long pending,
            long processing,
            long completed,
            long failed,
            long canceled,
            boolean paused
    ) {
    }

    public record QueueOperationResult(int affected) {
    }
}

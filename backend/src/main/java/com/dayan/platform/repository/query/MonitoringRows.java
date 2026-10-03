package com.dayan.platform.repository.query;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public final class MonitoringRows {

    private MonitoringRows() {
    }

    public static class MetricSampleRow {
        public long id;
        public long databaseLatencyMs;
        public Long redisLatencyMs;
        public int queueBacklog;
        public BigDecimal cpuUsagePercent;
        public BigDecimal memoryUsagePercent;
        public OffsetDateTime collectedAt;
    }

    public static class AccessLogRow {
        public long id;
        public Long userId;
        public String username;
        public String method;
        public String requestPath;
        public int statusCode;
        public long durationMs;
        public String ipAddress;
        public String userAgent;
        public String requestId;
        public OffsetDateTime occurredAt;
    }

    public static class OnlineUserRow {
        public long sessionId;
        public long userId;
        public String username;
        public String displayName;
        public String roles;
        public OffsetDateTime activeAt;
        public OffsetDateTime sessionStartedAt;
        public String currentPath;
        public String ipAddress;
    }

    public static class LoginLogRow {
        public long id;
        public String username;
        public String result;
        public String errorSummary;
        public String ipAddress;
        public OffsetDateTime occurredAt;
    }

    public static class ExportTaskRow {
        public long id;
        public String name;
        public String format;
        public String status;
        public int progress;
        public int processedCount;
        public int datasetCount;
        public String fileName;
        public Long fileSize;
        public String errorMessage;
        public long creatorId;
        public String creatorName;
        public OffsetDateTime startedAt;
        public OffsetDateTime completedAt;
        public OffsetDateTime createdAt;
    }

    public static class QueueCountsRow {
        public long pending;
        public long processing;
        public long completed;
        public long failed;
        public long canceled;
    }
}

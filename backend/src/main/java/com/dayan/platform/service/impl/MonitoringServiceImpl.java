package com.dayan.platform.service.impl;

import com.dayan.platform.common.api.ErrorCode;
import com.dayan.platform.common.exception.BusinessException;
import com.dayan.platform.config.MinioProperties;
import com.dayan.platform.dto.MonitoringDtos.AccessLogQuery;
import com.dayan.platform.dto.MonitoringDtos.ExportTaskQuery;
import com.dayan.platform.dto.MonitoringDtos.LoginLogQuery;
import com.dayan.platform.dto.MonitoringDtos.TimeRangePageQuery;
import com.dayan.platform.model.ApplicationMetadata;
import com.dayan.platform.repository.ApplicationMetadataRepository;
import com.dayan.platform.repository.mapper.MonitoringMapper;
import com.dayan.platform.repository.query.MonitoringRows.ExportTaskRow;
import com.dayan.platform.repository.query.MonitoringRows.MetricSampleRow;
import com.dayan.platform.repository.query.MonitoringRows.QueueCountsRow;
import com.dayan.platform.service.MonitoringService;
import com.dayan.platform.vo.MonitoringViews.AccessLog;
import com.dayan.platform.vo.MonitoringViews.ComponentStatus;
import com.dayan.platform.vo.MonitoringViews.ExportTask;
import com.dayan.platform.vo.MonitoringViews.LoginLog;
import com.dayan.platform.vo.MonitoringViews.MetricPoint;
import com.dayan.platform.vo.MonitoringViews.OnlineUser;
import com.dayan.platform.vo.MonitoringViews.OnlineUsers;
import com.dayan.platform.vo.MonitoringViews.Overview;
import com.dayan.platform.vo.MonitoringViews.QueueOperationResult;
import com.dayan.platform.vo.MonitoringViews.QueueSummary;
import com.dayan.platform.vo.MonitoringViews.ServiceStatus;
import com.dayan.platform.vo.MonitoringViews.SystemInformation;
import com.dayan.platform.vo.PageResponse;
import com.sun.management.OperatingSystemMXBean;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class MonitoringServiceImpl implements MonitoringService {

    private static final int METRIC_RETENTION_DAYS = 8;

    private final MonitoringMapper monitoringMapper;
    private final ApplicationMetadataRepository metadataRepository;
    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    public MonitoringServiceImpl(
            MonitoringMapper monitoringMapper,
            ApplicationMetadataRepository metadataRepository,
            MinioClient minioClient,
            MinioProperties minioProperties
    ) {
        this.monitoringMapper = monitoringMapper;
        this.metadataRepository = metadataRepository;
        this.minioClient = minioClient;
        this.minioProperties = minioProperties;
    }

    @Override
    @Transactional(readOnly = true)
    public Overview overview(String range) {
        Duration duration = switch (range == null ? "1h" : range.toLowerCase(Locale.ROOT)) {
            case "1h" -> Duration.ofHours(1);
            case "24h" -> Duration.ofHours(24);
            case "7d" -> Duration.ofDays(7);
            default -> throw new BusinessException(
                    ErrorCode.INVALID_ARGUMENT,
                    "range must be 1h, 24h or 7d"
            );
        };
        String normalizedRange = range == null ? "1h" : range.toLowerCase(Locale.ROOT);
        List<MetricPoint> points = monitoringMapper.selectMetricsSince(
                OffsetDateTime.now(ZoneOffset.UTC).minus(duration)
        ).stream().map(this::metricPoint).toList();
        MetricPoint latest = points.isEmpty() ? currentMetric(false) : points.getLast();
        return new Overview(
                latest,
                points,
                componentStatuses(latest),
                normalizedRange,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    @Override
    @Transactional
    public MetricPoint collect() {
        MetricPoint metric = currentMetric(true);
        monitoringMapper.deleteOldMetrics(
                OffsetDateTime.now(ZoneOffset.UTC).minusDays(METRIC_RETENTION_DAYS)
        );
        return metric;
    }

    @Override
    public SystemInformation systemInformation() {
        ApplicationMetadata metadata = metadataRepository.get();
        var runtime = ManagementFactory.getRuntimeMXBean();
        OperatingSystemMXBean operatingSystem = operatingSystem();
        long totalMemory = totalMemory(operatingSystem);
        long freeMemory = freeMemory(operatingSystem);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return new SystemInformation(
                metadata.name(),
                metadata.environment(),
                metadata.version(),
                hostname(),
                System.getProperty("os.name") + " " + System.getProperty("os.version"),
                Runtime.getRuntime().availableProcessors(),
                totalMemory,
                Math.max(totalMemory - freeMemory, 0),
                runtime.getUptime() / 1000,
                OffsetDateTime.ofInstant(
                        Instant.ofEpochMilli(runtime.getStartTime()),
                        ZoneOffset.UTC
                ),
                List.of(databaseStatus(), redisStatus(), storageStatus()),
                now
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AccessLog> accessLogs(AccessLogQuery query) {
        validateRange(query);
        String path = normalize(query.getPath());
        String username = normalize(query.getUsername());
        long total = monitoringMapper.countAccessLogs(
                path,
                username,
                query.getStatusCode(),
                query.getStartTime(),
                query.getEndTime()
        );
        long offset = (long) (query.getPage() - 1) * query.getSize();
        List<AccessLog> items = monitoringMapper.selectAccessLogs(
                path,
                username,
                query.getStatusCode(),
                query.getStartTime(),
                query.getEndTime(),
                query.getSize(),
                offset
        ).stream().map(row -> new AccessLog(
                row.id,
                row.userId,
                row.username,
                row.method,
                row.requestPath,
                row.statusCode,
                row.durationMs,
                row.ipAddress,
                row.userAgent,
                row.requestId,
                row.occurredAt
        )).toList();
        return PageResponse.of(query.getPage(), query.getSize(), total, items);
    }

    @Override
    @Transactional(readOnly = true)
    public OnlineUsers onlineUsers() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<OnlineUser> users = monitoringMapper.selectOnlineUsers().stream()
                .map(row -> new OnlineUser(
                        row.sessionId,
                        row.userId,
                        row.username,
                        row.displayName,
                        roles(row.roles),
                        row.activeAt,
                        Math.max(Duration.between(row.sessionStartedAt, now).toSeconds(), 0),
                        row.currentPath,
                        row.ipAddress
                ))
                .toList();
        return new OnlineUsers(
                users.size(),
                monitoringMapper.countTodayActiveUsers(),
                users,
                now
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<LoginLog> loginLogs(LoginLogQuery query) {
        validateRange(query);
        String username = normalize(query.getUsername());
        String ipAddress = normalize(query.getIpAddress());
        String result = upper(query.getResult());
        long total = monitoringMapper.countLoginLogs(
                username,
                ipAddress,
                result,
                query.getStartTime(),
                query.getEndTime()
        );
        long offset = (long) (query.getPage() - 1) * query.getSize();
        List<LoginLog> items = monitoringMapper.selectLoginLogs(
                username,
                ipAddress,
                result,
                query.getStartTime(),
                query.getEndTime(),
                query.getSize(),
                offset
        ).stream().map(row -> new LoginLog(
                row.id,
                row.username,
                row.result,
                row.errorSummary,
                row.ipAddress,
                row.occurredAt
        )).toList();
        return PageResponse.of(query.getPage(), query.getSize(), total, items);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ExportTask> exportTasks(ExportTaskQuery query) {
        validateRange(query);
        String keyword = normalize(query.getKeyword());
        String username = normalize(query.getUsername());
        String format = upper(query.getFormat());
        String status = upper(query.getStatus());
        long total = monitoringMapper.countExportTasks(
                keyword,
                username,
                format,
                status,
                query.getStartTime(),
                query.getEndTime()
        );
        long offset = (long) (query.getPage() - 1) * query.getSize();
        List<ExportTask> items = monitoringMapper.selectExportTasks(
                keyword,
                username,
                format,
                status,
                query.getStartTime(),
                query.getEndTime(),
                query.getSize(),
                offset
        ).stream().map(this::exportTask).toList();
        return PageResponse.of(query.getPage(), query.getSize(), total, items);
    }

    @Override
    @Transactional(readOnly = true)
    public QueueSummary queueSummary() {
        QueueCountsRow counts = monitoringMapper.selectQueueCounts();
        return new QueueSummary(
                counts.pending,
                counts.processing,
                counts.completed,
                counts.failed,
                counts.canceled,
                monitoringMapper.isExportQueuePaused()
        );
    }

    @Override
    @Transactional
    public QueueSummary setQueuePaused(boolean paused, long userId) {
        monitoringMapper.setExportQueuePaused(paused, userId);
        return queueSummary();
    }

    @Override
    @Transactional
    public QueueOperationResult retryTask(long id) {
        return requireAffected(monitoringMapper.retryExportTask(id), "Only failed tasks can be retried");
    }

    @Override
    @Transactional
    public QueueOperationResult cancelTask(long id) {
        return requireAffected(monitoringMapper.cancelExportTask(id), "Only pending tasks can be canceled");
    }

    @Override
    @Transactional
    public QueueOperationResult deleteTask(long id) {
        return requireAffected(
                monitoringMapper.deleteExportTask(id),
                "Only completed, failed or canceled tasks can be deleted"
        );
    }

    @Override
    @Transactional
    public QueueOperationResult retryAllFailed() {
        return new QueueOperationResult(monitoringMapper.retryAllFailedExportTasks());
    }

    @Override
    @Transactional
    public QueueOperationResult clearPending() {
        return new QueueOperationResult(monitoringMapper.clearPendingExportTasks());
    }

    @Override
    @Transactional
    public QueueOperationResult cleanHistory() {
        return new QueueOperationResult(monitoringMapper.cleanExportHistory());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isExportQueuePaused() {
        return monitoringMapper.isExportQueuePaused();
    }

    private MetricPoint currentMetric(boolean persist) {
        long started = System.nanoTime();
        monitoringMapper.pingDatabase();
        long databaseLatency = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        OperatingSystemMXBean operatingSystem = operatingSystem();
        BigDecimal cpu = percentage(operatingSystem == null ? -1 : operatingSystem.getCpuLoad());
        long total = totalMemory(operatingSystem);
        long free = freeMemory(operatingSystem);
        BigDecimal memory = total == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf((double) (total - free) * 100 / total)
                        .setScale(2, RoundingMode.HALF_UP);
        int backlog = monitoringMapper.countQueueBacklog();
        OffsetDateTime collectedAt = OffsetDateTime.now(ZoneOffset.UTC);
        if (persist) {
            monitoringMapper.insertMetric(
                    databaseLatency,
                    null,
                    backlog,
                    cpu,
                    memory,
                    collectedAt
            );
        }
        return new MetricPoint(databaseLatency, null, backlog, cpu, memory, collectedAt);
    }

    private MetricPoint metricPoint(MetricSampleRow row) {
        return new MetricPoint(
                row.databaseLatencyMs,
                row.redisLatencyMs,
                row.queueBacklog,
                row.cpuUsagePercent,
                row.memoryUsagePercent,
                row.collectedAt
        );
    }

    private List<ComponentStatus> componentStatuses(MetricPoint metric) {
        ServiceStatus storage = storageStatus();
        return List.of(
                new ComponentStatus(
                        "DATABASE",
                        thresholdStatus(metric.databaseLatencyMs(), 200, 1_000),
                        metric.databaseLatencyMs()
                ),
                new ComponentStatus(
                        "REDIS",
                        metric.redisLatencyMs() == null
                                ? "NOT_CONFIGURED"
                                : thresholdStatus(metric.redisLatencyMs(), 200, 1_000),
                        metric.redisLatencyMs()
                ),
                new ComponentStatus(
                        "MINIO",
                        "UP".equals(storage.status()) ? "HEALTHY" : "CRITICAL",
                        storage.latencyMs()
                ),
                new ComponentStatus(
                        "QUEUE",
                        monitoringMapper.isExportQueuePaused()
                                ? "PAUSED"
                                : thresholdStatus(metric.queueBacklog(), 1, 100),
                        null
                ),
                new ComponentStatus(
                        "CPU",
                        thresholdStatus(metric.cpuUsagePercent(), 70, 85),
                        null
                ),
                new ComponentStatus(
                        "MEMORY",
                        thresholdStatus(metric.memoryUsagePercent(), 75, 90),
                        null
                )
        );
    }

    private String thresholdStatus(long value, long warningThreshold, long criticalThreshold) {
        if (value >= criticalThreshold) {
            return "CRITICAL";
        }
        return value >= warningThreshold ? "WARNING" : "HEALTHY";
    }

    private String thresholdStatus(BigDecimal value, int warningThreshold, int criticalThreshold) {
        if (value.compareTo(BigDecimal.valueOf(criticalThreshold)) >= 0) {
            return "CRITICAL";
        }
        return value.compareTo(BigDecimal.valueOf(warningThreshold)) >= 0 ? "WARNING" : "HEALTHY";
    }

    private ServiceStatus databaseStatus() {
        long started = System.nanoTime();
        try {
            monitoringMapper.pingDatabase();
            long latency = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            return new ServiceStatus("PostgreSQL", "UP", latency, "Connection available");
        } catch (RuntimeException exception) {
            return new ServiceStatus("PostgreSQL", "DOWN", null, "Connection unavailable");
        }
    }

    private ServiceStatus redisStatus() {
        return new ServiceStatus("Redis", "NOT_CONFIGURED", null, "Redis is not configured");
    }

    private ServiceStatus storageStatus() {
        long started = System.nanoTime();
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(minioProperties.bucket()).build()
            );
            long latency = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            return new ServiceStatus(
                    "MinIO",
                    exists ? "UP" : "DOWN",
                    latency,
                    exists ? minioProperties.bucket() : "Bucket unavailable"
            );
        } catch (Exception exception) {
            return new ServiceStatus("MinIO", "DOWN", null, "Storage unavailable");
        }
    }

    private ExportTask exportTask(ExportTaskRow row) {
        return new ExportTask(
                row.id,
                row.name,
                row.format,
                row.status,
                row.progress,
                row.processedCount,
                row.datasetCount,
                row.fileName,
                row.fileSize,
                row.errorMessage,
                row.creatorId,
                row.creatorName,
                row.startedAt,
                row.completedAt,
                row.createdAt
        );
    }

    private QueueOperationResult requireAffected(int affected, String message) {
        if (affected != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, message);
        }
        return new QueueOperationResult(affected);
    }

    private void validateRange(TimeRangePageQuery query) {
        if (query.getStartTime() != null
                && query.getEndTime() != null
                && query.getStartTime().isAfter(query.getEndTime())) {
            throw new BusinessException(
                    ErrorCode.INVALID_ARGUMENT,
                    "startTime must not be after endTime"
            );
        }
    }

    private List<String> roles(String value) {
        return StringUtils.hasText(value)
                ? Arrays.stream(value.split(",")).map(String::trim).filter(StringUtils::hasText).toList()
                : List.of();
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String upper(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private OperatingSystemMXBean operatingSystem() {
        return ManagementFactory.getOperatingSystemMXBean() instanceof OperatingSystemMXBean bean
                ? bean
                : null;
    }

    private long totalMemory(OperatingSystemMXBean bean) {
        return bean == null ? Runtime.getRuntime().maxMemory() : bean.getTotalMemorySize();
    }

    private long freeMemory(OperatingSystemMXBean bean) {
        return bean == null ? Runtime.getRuntime().freeMemory() : bean.getFreeMemorySize();
    }

    private BigDecimal percentage(double ratio) {
        if (ratio < 0 || Double.isNaN(ratio)) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(Math.min(ratio * 100, 100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception exception) {
            return "unknown";
        }
    }
}

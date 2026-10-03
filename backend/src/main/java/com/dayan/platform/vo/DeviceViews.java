package com.dayan.platform.vo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class DeviceViews {

    private DeviceViews() {
    }

    public record DeviceSummary(
            long id,
            String agentId,
            String deviceCode,
            String remark,
            Long robotId,
            String robotName,
            Long projectId,
            String projectName,
            Long collectionTaskId,
            String collectionTaskName,
            boolean activated,
            boolean online,
            OffsetDateTime lastReportAt,
            String hostname,
            String operatingSystem,
            String platform,
            String kernelVersion,
            List<String> ipAddresses,
            BigDecimal cpuUsage,
            BigDecimal memoryUsage,
            BigDecimal diskUsage,
            BigDecimal cpuTemperature,
            Long memoryUsedBytes,
            Long diskAvailableBytes,
            int activeTcpConnections,
            long uptimeSeconds,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record DeviceMetric(
            BigDecimal cpuUsage,
            BigDecimal memoryUsage,
            BigDecimal diskUsage,
            BigDecimal cpuTemperature,
            int activeTcpConnections,
            OffsetDateTime reportedAt
    ) {
    }

    public record DeviceDetail(
            DeviceSummary device,
            List<DeviceMetric> metrics
    ) {
    }

    public record DeviceRegistration(
            DeviceSummary device,
            String agentToken
    ) {
    }

    public record InstallCommand(String command) {
    }

    public record DeviceOption(long id, String name) {
    }

    public record DeviceOptions(
            List<DeviceOption> robots,
            List<DeviceOption> projects,
            List<DeviceOption> collectionTasks
    ) {
    }

    public record BatchResult(int succeeded, int failed) {
    }
}

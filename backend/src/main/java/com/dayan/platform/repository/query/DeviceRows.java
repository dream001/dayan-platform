package com.dayan.platform.repository.query;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public final class DeviceRows {

    private DeviceRows() {
    }

    public static class DeviceRow {
        public Long id;
        public String agentId;
        public String agentToken;
        public String deviceCode;
        public String remark;
        public Long robotId;
        public String robotName;
        public Long projectId;
        public String projectName;
        public Long collectionTaskId;
        public String collectionTaskName;
        public OffsetDateTime lastReportAt;
        public String hostname;
        public String operatingSystem;
        public String platform;
        public String kernelVersion;
        public String ipAddresses;
        public BigDecimal cpuUsage;
        public BigDecimal memoryUsage;
        public BigDecimal diskUsage;
        public BigDecimal cpuTemperature;
        public Long memoryUsedBytes;
        public Long diskAvailableBytes;
        public Integer activeTcpConnections;
        public Long uptimeSeconds;
        public OffsetDateTime createdAt;
        public OffsetDateTime updatedAt;
    }

    public static class MetricRow {
        public BigDecimal cpuUsage;
        public BigDecimal memoryUsage;
        public BigDecimal diskUsage;
        public BigDecimal cpuTemperature;
        public Integer activeTcpConnections;
        public OffsetDateTime reportedAt;
    }

    public static class OptionRow {
        public Long id;
        public String name;
    }
}

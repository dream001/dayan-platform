package com.dayan.platform.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public final class DeviceDtos {

    private DeviceDtos() {
    }

    public record DeviceRequest(
            @NotBlank
            @Pattern(regexp = "^[A-Za-z0-9_-]{3,128}$")
            String deviceCode,
            @Size(max = 1000) String remark,
            @Positive Long robotId,
            @Positive Long projectId
    ) {
    }

    public record DeviceBatchRequest(
            @NotNull @Size(min = 1, max = 100) List<@Positive Long> deviceIds
    ) {
    }

    public record DeviceTaskRequest(
            @NotNull @Size(min = 1, max = 100) List<@Positive Long> deviceIds,
            @NotNull @Positive Long collectionTaskId
    ) {
    }

    public record AgentReportRequest(
            @NotBlank @Size(max = 255) String hostname,
            @NotBlank @Size(max = 255) String operatingSystem,
            @NotBlank @Size(max = 255) String platform,
            @Size(max = 255) String kernelVersion,
            @Size(max = 20) List<@Size(max = 64) String> ipAddresses,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal cpuUsage,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal memoryUsage,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal diskUsage,
            BigDecimal cpuTemperature,
            @Min(0) Long memoryUsedBytes,
            @Min(0) Long diskAvailableBytes,
            @NotNull @Min(0) Integer activeTcpConnections,
            @NotNull @Min(0) Long uptimeSeconds
    ) {
    }

    public record InstallOptions(
            @NotNull @Min(1) Integer pollIntervalSeconds,
            @NotNull @Min(1) Integer videoFps,
            @Min(0) Integer rosDomainId,
            @Size(max = 255) String imageTopic,
            @Min(1) Integer maxVideoWidth,
            @NotNull Boolean remoteControlEnabled,
            @Size(min = 8, max = 128) String connectionPassword,
            @NotNull Boolean verbose
    ) {
    }
}

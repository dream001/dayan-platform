package com.dayan.platform.service.impl;

import com.dayan.platform.common.api.ErrorCode;
import com.dayan.platform.common.exception.BusinessException;
import com.dayan.platform.dto.DeviceDtos.AgentReportRequest;
import com.dayan.platform.dto.DeviceDtos.DeviceRequest;
import com.dayan.platform.dto.DeviceDtos.InstallOptions;
import com.dayan.platform.model.Device;
import com.dayan.platform.repository.mapper.DeviceMapper;
import com.dayan.platform.repository.query.DeviceRows.DeviceRow;
import com.dayan.platform.repository.query.DeviceRows.MetricRow;
import com.dayan.platform.repository.query.DeviceRows.OptionRow;
import com.dayan.platform.service.DeviceService;
import com.dayan.platform.vo.DeviceViews.BatchResult;
import com.dayan.platform.vo.DeviceViews.DeviceDetail;
import com.dayan.platform.vo.DeviceViews.DeviceMetric;
import com.dayan.platform.vo.DeviceViews.DeviceOption;
import com.dayan.platform.vo.DeviceViews.DeviceOptions;
import com.dayan.platform.vo.DeviceViews.DeviceRegistration;
import com.dayan.platform.vo.DeviceViews.DeviceSummary;
import com.dayan.platform.vo.DeviceViews.InstallCommand;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DeviceServiceImpl implements DeviceService {

    private static final int ONLINE_WINDOW_MINUTES = 10;
    private final DeviceMapper deviceMapper;

    public DeviceServiceImpl(DeviceMapper deviceMapper) {
        this.deviceMapper = deviceMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeviceSummary> list(String keyword, Long projectId, long userId, boolean admin) {
        return deviceMapper.selectDevices(userId, admin, trimToNull(keyword), projectId)
                .stream()
                .map(this::summary)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DeviceDetail detail(UUID agentId, int hours, long userId, boolean admin) {
        DeviceRow row = deviceMapper.selectAccessibleByAgentId(agentId.toString(), userId, admin);
        if (row == null) {
            throw notFound();
        }
        List<DeviceMetric> metrics = deviceMapper.selectMetrics(
                row.id,
                OffsetDateTime.now(ZoneOffset.UTC).minusHours(hours)
        ).stream().map(this::metric).toList();
        return new DeviceDetail(summary(row), metrics);
    }

    @Override
    @Transactional
    public DeviceRegistration create(DeviceRequest request, long userId, boolean admin) {
        validateProject(request.projectId(), userId, admin);
        Device existing = deviceMapper.selectAnyByCode(request.deviceCode().trim());
        if (existing != null) {
            if (!Boolean.TRUE.equals(existing.getDeleted()) || existing.getCreatedBy() != userId) {
                throw new BusinessException(ErrorCode.CONFLICT, "Device code already exists");
            }
            apply(existing, request);
            deviceMapper.updateRegistration(existing);
            DeviceSummary restored = findById(existing.getId(), userId, admin);
            return new DeviceRegistration(restored, existing.getAgentToken());
        }

        Device device = new Device();
        apply(device, request);
        device.setAgentId(UUID.randomUUID().toString());
        device.setAgentToken(UUID.randomUUID().toString());
        device.setCreatedBy(userId);
        device.setDeleted(false);
        try {
            deviceMapper.insertDevice(device);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "Device code already exists");
        }
        return new DeviceRegistration(
                findById(device.getId(), userId, admin),
                device.getAgentToken()
        );
    }

    @Override
    @Transactional
    public DeviceSummary update(
            long id,
            DeviceRequest request,
            long userId,
            boolean admin
    ) {
        validateProject(request.projectId(), userId, admin);
        Device device = requireAccessible(id, userId, admin);
        Device duplicate = deviceMapper.selectAnyByCode(request.deviceCode().trim());
        if (duplicate != null && !duplicate.getId().equals(id)) {
            throw new BusinessException(ErrorCode.CONFLICT, "Device code already exists");
        }
        apply(device, request);
        deviceMapper.updateRegistration(device);
        return findById(id, userId, admin);
    }

    @Override
    @Transactional
    public BatchResult delete(List<Long> deviceIds, long userId, boolean admin) {
        int succeeded = 0;
        for (Long id : deviceIds.stream().distinct().toList()) {
            try {
                requireAccessible(id, userId, admin);
                succeeded += deviceMapper.softDelete(id);
            } catch (BusinessException ignored) {
                // Batch operations report inaccessible or missing rows as failures.
            }
        }
        return new BatchResult(succeeded, deviceIds.stream().distinct().toList().size() - succeeded);
    }

    @Override
    @Transactional
    public BatchResult assignTask(
            List<Long> deviceIds,
            long taskId,
            long userId,
            boolean admin
    ) {
        boolean taskAllowed = deviceMapper.selectTaskOptions(userId, admin)
                .stream()
                .anyMatch(task -> task.id == taskId);
        if (!taskAllowed) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Collection task is not accessible");
        }
        int succeeded = 0;
        for (Long id : deviceIds.stream().distinct().toList()) {
            try {
                requireAccessible(id, userId, admin);
                succeeded += deviceMapper.assignTask(id, taskId);
            } catch (BusinessException ignored) {
                // Continue so the caller receives an aggregate result.
            }
        }
        return new BatchResult(succeeded, deviceIds.stream().distinct().toList().size() - succeeded);
    }

    @Override
    @Transactional(readOnly = true)
    public DeviceOptions options(long userId, boolean admin) {
        return new DeviceOptions(
                options(deviceMapper.selectRobotOptions()),
                options(deviceMapper.selectProjectOptions(userId, admin)),
                options(deviceMapper.selectTaskOptions(userId, admin))
        );
    }

    @Override
    @Transactional(readOnly = true)
    public InstallCommand installCommand(
            long id,
            InstallOptions options,
            long userId,
            boolean admin
    ) {
        Device device = requireAccessible(id, userId, admin);
        if (Boolean.TRUE.equals(options.remoteControlEnabled())
                && !StringUtils.hasText(options.connectionPassword())) {
            throw new BusinessException(
                    ErrorCode.INVALID_ARGUMENT,
                    "Connection password is required when remote control is enabled"
            );
        }
        if (options.rosDomainId() != null && options.rosDomainId() > 232) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "ROS_DOMAIN_ID must not exceed 232");
        }
        StringBuilder command = new StringBuilder(
                "curl -fsSL \"${DAYAN_SERVER_URL}/agent/install.sh\" | sudo bash -s --"
        );
        argument(command, "--server", "${DAYAN_SERVER_URL}");
        argument(command, "--agent-id", device.getAgentId());
        argument(command, "--token", device.getAgentToken());
        argument(command, "--interval", options.pollIntervalSeconds().toString());
        argument(command, "--fps", options.videoFps().toString());
        optionalArgument(command, "--ros-domain-id", options.rosDomainId());
        optionalArgument(command, "--image-topic", options.imageTopic());
        optionalArgument(command, "--max-video-width", options.maxVideoWidth());
        if (Boolean.TRUE.equals(options.remoteControlEnabled())) {
            argument(command, "--remote-control", "true");
            argument(command, "--connection-password", options.connectionPassword());
        }
        if (Boolean.TRUE.equals(options.verbose())) {
            argument(command, "--verbose", "true");
        }
        return new InstallCommand(command.toString());
    }

    @Override
    @Transactional
    public void report(UUID token, AgentReportRequest report) {
        Device device = deviceMapper.selectByAgentToken(token.toString());
        if (device == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid agent token");
        }
        deviceMapper.insertReport(device.getId(), report);
        String addresses = report.ipAddresses() == null
                ? null
                : String.join(",", report.ipAddresses());
        deviceMapper.updateSystemInfo(device.getId(), report, addresses);
    }

    private void apply(Device device, DeviceRequest request) {
        device.setDeviceCode(request.deviceCode().trim());
        device.setRemark(trimToNull(request.remark()));
        device.setRobotId(request.robotId());
        device.setProjectId(request.projectId());
    }

    private void validateProject(Long projectId, long userId, boolean admin) {
        if (projectId == null) {
            return;
        }
        boolean allowed = deviceMapper.selectProjectOptions(userId, admin)
                .stream()
                .anyMatch(project -> project.id.equals(projectId));
        if (!allowed) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Project edit permission is required");
        }
    }

    private Device requireAccessible(long id, long userId, boolean admin) {
        Device device = deviceMapper.selectActiveById(id);
        if (device == null) {
            throw notFound();
        }
        boolean accessible = deviceMapper.selectDevices(userId, admin, null, null)
                .stream()
                .anyMatch(row -> row.id == id);
        if (!accessible) {
            throw notFound();
        }
        return device;
    }

    private DeviceSummary findById(long id, long userId, boolean admin) {
        return deviceMapper.selectDevices(userId, admin, null, null).stream()
                .filter(row -> row.id == id)
                .findFirst()
                .map(this::summary)
                .orElseThrow(this::notFound);
    }

    private DeviceSummary summary(DeviceRow row) {
        boolean activated = row.lastReportAt != null;
        boolean online = activated && row.lastReportAt.isAfter(
                OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(ONLINE_WINDOW_MINUTES)
        );
        List<String> addresses = !StringUtils.hasText(row.ipAddresses)
                ? List.of()
                : Arrays.stream(row.ipAddresses.split(","))
                        .map(String::trim)
                        .filter(StringUtils::hasText)
                        .toList();
        return new DeviceSummary(
                row.id, row.agentId, row.deviceCode, row.remark, row.robotId, row.robotName,
                row.projectId, row.projectName, row.collectionTaskId, row.collectionTaskName,
                activated, online, row.lastReportAt, row.hostname, row.operatingSystem,
                row.platform, row.kernelVersion, addresses, row.cpuUsage, row.memoryUsage,
                row.diskUsage, row.cpuTemperature, row.memoryUsedBytes, row.diskAvailableBytes,
                row.activeTcpConnections == null ? 0 : row.activeTcpConnections,
                row.uptimeSeconds == null ? 0 : row.uptimeSeconds, row.createdAt, row.updatedAt
        );
    }

    private DeviceMetric metric(MetricRow row) {
        return new DeviceMetric(
                row.cpuUsage,
                row.memoryUsage,
                row.diskUsage,
                row.cpuTemperature,
                row.activeTcpConnections,
                row.reportedAt
        );
    }

    private List<DeviceOption> options(List<OptionRow> rows) {
        return rows.stream().map(row -> new DeviceOption(row.id, row.name)).toList();
    }

    private void argument(StringBuilder command, String name, String value) {
        command.append(' ').append(name).append(" '")
                .append(value.replace("'", "'\"'\"'"))
                .append('\'');
    }

    private void optionalArgument(StringBuilder command, String name, Object value) {
        if (value != null && StringUtils.hasText(value.toString())) {
            argument(command, name, value.toString());
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private BusinessException notFound() {
        return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Device not found");
    }
}

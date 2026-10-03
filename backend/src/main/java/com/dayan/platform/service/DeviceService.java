package com.dayan.platform.service;

import com.dayan.platform.dto.DeviceDtos.AgentReportRequest;
import com.dayan.platform.dto.DeviceDtos.DeviceRequest;
import com.dayan.platform.dto.DeviceDtos.InstallOptions;
import com.dayan.platform.vo.DeviceViews.BatchResult;
import com.dayan.platform.vo.DeviceViews.DeviceDetail;
import com.dayan.platform.vo.DeviceViews.DeviceOptions;
import com.dayan.platform.vo.DeviceViews.DeviceRegistration;
import com.dayan.platform.vo.DeviceViews.DeviceSummary;
import com.dayan.platform.vo.DeviceViews.InstallCommand;
import java.util.List;
import java.util.UUID;

public interface DeviceService {

    List<DeviceSummary> list(String keyword, Long projectId, long userId, boolean admin);

    DeviceDetail detail(UUID agentId, int hours, long userId, boolean admin);

    DeviceRegistration create(DeviceRequest request, long userId, boolean admin);

    DeviceSummary update(long id, DeviceRequest request, long userId, boolean admin);

    BatchResult delete(List<Long> deviceIds, long userId, boolean admin);

    BatchResult assignTask(List<Long> deviceIds, long taskId, long userId, boolean admin);

    DeviceOptions options(long userId, boolean admin);

    InstallCommand installCommand(long id, InstallOptions options, long userId, boolean admin);

    void report(UUID token, AgentReportRequest report);
}

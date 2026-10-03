package com.dayan.platform.service;

import com.dayan.platform.dto.MonitoringDtos.AccessLogQuery;
import com.dayan.platform.dto.MonitoringDtos.ExportTaskQuery;
import com.dayan.platform.dto.MonitoringDtos.LoginLogQuery;
import com.dayan.platform.vo.MonitoringViews.AccessLog;
import com.dayan.platform.vo.MonitoringViews.ExportTask;
import com.dayan.platform.vo.MonitoringViews.LoginLog;
import com.dayan.platform.vo.MonitoringViews.MetricPoint;
import com.dayan.platform.vo.MonitoringViews.OnlineUsers;
import com.dayan.platform.vo.MonitoringViews.Overview;
import com.dayan.platform.vo.MonitoringViews.QueueOperationResult;
import com.dayan.platform.vo.MonitoringViews.QueueSummary;
import com.dayan.platform.vo.MonitoringViews.SystemInformation;
import com.dayan.platform.vo.PageResponse;

public interface MonitoringService {

    Overview overview(String range);

    MetricPoint collect();

    SystemInformation systemInformation();

    PageResponse<AccessLog> accessLogs(AccessLogQuery query);

    OnlineUsers onlineUsers();

    PageResponse<LoginLog> loginLogs(LoginLogQuery query);

    PageResponse<ExportTask> exportTasks(ExportTaskQuery query);

    QueueSummary queueSummary();

    QueueSummary setQueuePaused(boolean paused, long userId);

    QueueOperationResult retryTask(long id);

    QueueOperationResult cancelTask(long id);

    QueueOperationResult deleteTask(long id);

    QueueOperationResult retryAllFailed();

    QueueOperationResult clearPending();

    QueueOperationResult cleanHistory();

    boolean isExportQueuePaused();
}

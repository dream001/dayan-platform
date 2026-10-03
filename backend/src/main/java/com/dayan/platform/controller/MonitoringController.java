package com.dayan.platform.controller;

import com.dayan.platform.audit.Audited;
import com.dayan.platform.dto.MonitoringDtos.AccessLogQuery;
import com.dayan.platform.dto.MonitoringDtos.ExportTaskQuery;
import com.dayan.platform.dto.MonitoringDtos.LoginLogQuery;
import com.dayan.platform.service.MonitoringService;
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
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${app.api.base-path}/monitor")
@PreAuthorize("hasAuthority('basic:operations:view')")
public class MonitoringController {

    private final MonitoringService monitoringService;

    public MonitoringController(MonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }

    @GetMapping("/overview")
    public Overview overview(@RequestParam(defaultValue = "1h") String range) {
        return monitoringService.overview(range);
    }

    @PostMapping("/collect")
    @PreAuthorize("hasAuthority('basic:operations:collect')")
    @Audited(module = "OPERATIONS", action = "COLLECT_METRICS", targetType = "MONITOR")
    public MetricPoint collect() {
        return monitoringService.collect();
    }

    @GetMapping("/system")
    public SystemInformation systemInformation() {
        return monitoringService.systemInformation();
    }

    @GetMapping("/access-logs")
    public PageResponse<AccessLog> accessLogs(
            @Valid @ModelAttribute AccessLogQuery query
    ) {
        return monitoringService.accessLogs(query);
    }

    @GetMapping("/online-users")
    public OnlineUsers onlineUsers() {
        return monitoringService.onlineUsers();
    }

    @GetMapping("/login-logs")
    public PageResponse<LoginLog> loginLogs(
            @Valid @ModelAttribute LoginLogQuery query
    ) {
        return monitoringService.loginLogs(query);
    }

    @GetMapping("/exports")
    public PageResponse<ExportTask> exportTasks(
            @Valid @ModelAttribute ExportTaskQuery query
    ) {
        return monitoringService.exportTasks(query);
    }

    @GetMapping("/queue")
    public QueueSummary queueSummary() {
        return monitoringService.queueSummary();
    }

    @PostMapping("/queue/pause")
    @PreAuthorize("hasAuthority('basic:operations:queue')")
    @Audited(module = "OPERATIONS", action = "SET_QUEUE_STATE", targetType = "EXPORT_QUEUE")
    public QueueSummary setQueuePaused(
            @RequestParam boolean paused,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return monitoringService.setQueuePaused(paused, userId(jwt));
    }

    @PostMapping("/queue/tasks/{id}/retry")
    @PreAuthorize("hasAuthority('basic:operations:queue')")
    @Audited(
            module = "OPERATIONS",
            action = "RETRY_EXPORT",
            targetType = "EXPORT_TASK",
            targetId = "#id"
    )
    public QueueOperationResult retryTask(@PathVariable long id) {
        return monitoringService.retryTask(id);
    }

    @PostMapping("/queue/tasks/{id}/cancel")
    @PreAuthorize("hasAuthority('basic:operations:queue')")
    @Audited(
            module = "OPERATIONS",
            action = "CANCEL_EXPORT",
            targetType = "EXPORT_TASK",
            targetId = "#id"
    )
    public QueueOperationResult cancelTask(@PathVariable long id) {
        return monitoringService.cancelTask(id);
    }

    @DeleteMapping("/queue/tasks/{id}")
    @PreAuthorize("hasAuthority('basic:operations:queue')")
    @Audited(
            module = "OPERATIONS",
            action = "DELETE_EXPORT",
            targetType = "EXPORT_TASK",
            targetId = "#id"
    )
    public QueueOperationResult deleteTask(@PathVariable long id) {
        return monitoringService.deleteTask(id);
    }

    @PostMapping("/queue/retry-failed")
    @PreAuthorize("hasAuthority('basic:operations:queue')")
    @Audited(module = "OPERATIONS", action = "RETRY_FAILED_EXPORTS", targetType = "EXPORT_QUEUE")
    public QueueOperationResult retryAllFailed() {
        return monitoringService.retryAllFailed();
    }

    @DeleteMapping("/queue/pending")
    @PreAuthorize("hasAuthority('basic:operations:queue')")
    @Audited(module = "OPERATIONS", action = "CLEAR_PENDING_EXPORTS", targetType = "EXPORT_QUEUE")
    public QueueOperationResult clearPending() {
        return monitoringService.clearPending();
    }

    @DeleteMapping("/queue/history")
    @PreAuthorize("hasAuthority('basic:operations:queue')")
    @Audited(module = "OPERATIONS", action = "CLEAN_EXPORT_HISTORY", targetType = "EXPORT_QUEUE")
    public QueueOperationResult cleanHistory() {
        return monitoringService.cleanHistory();
    }

    private long userId(Jwt jwt) {
        return ((Number) jwt.getClaim("uid")).longValue();
    }
}

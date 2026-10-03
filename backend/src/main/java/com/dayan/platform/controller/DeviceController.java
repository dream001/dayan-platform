package com.dayan.platform.controller;

import com.dayan.platform.audit.Audited;
import com.dayan.platform.dto.DeviceDtos.AgentReportRequest;
import com.dayan.platform.dto.DeviceDtos.DeviceBatchRequest;
import com.dayan.platform.dto.DeviceDtos.DeviceRequest;
import com.dayan.platform.dto.DeviceDtos.DeviceTaskRequest;
import com.dayan.platform.dto.DeviceDtos.InstallOptions;
import com.dayan.platform.service.DeviceService;
import com.dayan.platform.vo.DeviceViews.BatchResult;
import com.dayan.platform.vo.DeviceViews.DeviceDetail;
import com.dayan.platform.vo.DeviceViews.DeviceOptions;
import com.dayan.platform.vo.DeviceViews.DeviceRegistration;
import com.dayan.platform.vo.DeviceViews.DeviceSummary;
import com.dayan.platform.vo.DeviceViews.InstallCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("${app.api.base-path}/basic/devices")
public class DeviceController {

    private static final String PLATFORM_ADMIN_PERMISSION = "basic:project:update";
    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('basic:device:view')")
    public List<DeviceSummary> list(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long projectId
    ) {
        return deviceService.list(keyword, projectId, userId(jwt), isPlatformAdmin(authentication));
    }

    @GetMapping("/{agentId}")
    @PreAuthorize("hasAuthority('basic:device:view')")
    public DeviceDetail detail(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @PathVariable UUID agentId,
            @RequestParam(defaultValue = "4") @Min(1) @Max(168) int hours
    ) {
        return deviceService.detail(agentId, hours, userId(jwt), isPlatformAdmin(authentication));
    }

    @GetMapping("/options")
    @PreAuthorize("hasAuthority('basic:device:view')")
    public DeviceOptions options(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return deviceService.options(userId(jwt), isPlatformAdmin(authentication));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('basic:device:remote:manage')")
    @Audited(module = "DEVICE", action = "CREATE", targetType = "DEVICE", targetId = "#request.deviceCode()")
    public DeviceRegistration create(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @Valid @RequestBody DeviceRequest request
    ) {
        return deviceService.create(request, userId(jwt), isPlatformAdmin(authentication));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('basic:device:manage')")
    @Audited(module = "DEVICE", action = "UPDATE", targetType = "DEVICE", targetId = "#id")
    public DeviceSummary update(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @PathVariable long id,
            @Valid @RequestBody DeviceRequest request
    ) {
        return deviceService.update(id, request, userId(jwt), isPlatformAdmin(authentication));
    }

    @DeleteMapping
    @PreAuthorize("hasAuthority('basic:device:remote:manage')")
    @Audited(module = "DEVICE", action = "BATCH_DELETE", targetType = "DEVICE")
    public BatchResult delete(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @Valid @RequestBody DeviceBatchRequest request
    ) {
        return deviceService.delete(
                request.deviceIds(),
                userId(jwt),
                isPlatformAdmin(authentication)
        );
    }

    @PatchMapping("/collection-task")
    @PreAuthorize("hasAuthority('basic:device:manage')")
    @Audited(module = "DEVICE", action = "ASSIGN_TASK", targetType = "DEVICE")
    public BatchResult assignTask(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @Valid @RequestBody DeviceTaskRequest request
    ) {
        return deviceService.assignTask(
                request.deviceIds(),
                request.collectionTaskId(),
                userId(jwt),
                isPlatformAdmin(authentication)
        );
    }

    @PostMapping("/{id}/install-command")
    @PreAuthorize("hasAuthority('basic:device:remote:view')")
    public InstallCommand installCommand(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @PathVariable long id,
            @Valid @RequestBody InstallOptions options
    ) {
        return deviceService.installCommand(
                id,
                options,
                userId(jwt),
                isPlatformAdmin(authentication)
        );
    }

    @PostMapping("/agent/report")
    public void report(
            @RequestHeader("X-Agent-Token") UUID token,
            @Valid @RequestBody AgentReportRequest report
    ) {
        deviceService.report(token, report);
    }

    private long userId(Jwt jwt) {
        return ((Number) jwt.getClaim("uid")).longValue();
    }

    private boolean isPlatformAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> PLATFORM_ADMIN_PERMISSION.equals(authority.getAuthority()));
    }
}

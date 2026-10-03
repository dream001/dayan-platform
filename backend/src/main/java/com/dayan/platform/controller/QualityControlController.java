package com.dayan.platform.controller;

import com.dayan.platform.audit.Audited;
import com.dayan.platform.dto.QualityControlDtos.LogFilter;
import com.dayan.platform.dto.QualityControlDtos.OverrideRequest;
import com.dayan.platform.dto.QualityControlDtos.ReportRequest;
import com.dayan.platform.dto.QualityControlDtos.RuleRequest;
import com.dayan.platform.dto.QualityControlDtos.RunRequest;
import com.dayan.platform.service.QualityControlService;
import com.dayan.platform.vo.PageResponse;
import com.dayan.platform.vo.QualityControlViews.DatasetOption;
import com.dayan.platform.vo.QualityControlViews.ExecutionDetail;
import com.dayan.platform.vo.QualityControlViews.ExecutionView;
import com.dayan.platform.vo.QualityControlViews.Overview;
import com.dayan.platform.vo.QualityControlViews.ProjectOption;
import com.dayan.platform.vo.QualityControlViews.RuleView;
import com.dayan.platform.vo.QualityControlViews.RunResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("${app.api.base-path}/data/quality-check")
public class QualityControlController {

    private static final String GLOBAL_PERMISSION = "data:qc:rule:global";

    private final QualityControlService qualityService;

    public QualityControlController(QualityControlService qualityService) {
        this.qualityService = qualityService;
    }

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('data:qc:view')")
    public Overview overview(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return qualityService.overview(userId(jwt), isAdmin(authentication));
    }

    @GetMapping("/rules")
    @PreAuthorize("hasAuthority('data:qc:view')")
    public PageResponse<RuleView> rules(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @Positive Long projectId,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return qualityService.rules(
                page,
                size,
                projectId,
                enabled,
                keyword,
                userId(jwt),
                isAdmin(authentication)
        );
    }

    @PostMapping("/rules")
    @PreAuthorize("hasAuthority('data:qc:rule:manage')")
    @Audited(module = "DATA_QC", action = "CREATE_RULE", targetType = "QC_RULE",
            targetId = "#result.id()")
    public RuleView createRule(
            @Valid @RequestBody RuleRequest request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return qualityService.createRule(request, userId(jwt), isAdmin(authentication));
    }

    @PutMapping("/rules/{id}")
    @PreAuthorize("hasAuthority('data:qc:rule:manage')")
    @Audited(module = "DATA_QC", action = "UPDATE_RULE", targetType = "QC_RULE",
            targetId = "#id")
    public RuleView updateRule(
            @PathVariable long id,
            @Valid @RequestBody RuleRequest request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return qualityService.updateRule(id, request, userId(jwt), isAdmin(authentication));
    }

    @DeleteMapping("/rules/{id}")
    @PreAuthorize("hasAuthority('data:qc:rule:manage')")
    @Audited(module = "DATA_QC", action = "DELETE_RULE", targetType = "QC_RULE",
            targetId = "#id")
    public void deleteRule(
            @PathVariable long id,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        qualityService.deleteRule(id, userId(jwt), isAdmin(authentication));
    }

    @GetMapping("/projects")
    @PreAuthorize("hasAuthority('data:qc:view')")
    public List<ProjectOption> projects(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return qualityService.projectOptions(userId(jwt), isAdmin(authentication));
    }

    @GetMapping("/datasets")
    @PreAuthorize("hasAuthority('data:qc:view')")
    public List<DatasetOption> datasets(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return qualityService.datasetOptions(userId(jwt), isAdmin(authentication));
    }

    @PostMapping("/datasets/{datasetId}/run")
    @PreAuthorize("hasAuthority('data:qc:execute')")
    @Audited(module = "DATA_QC", action = "RUN", targetType = "DATASET",
            targetId = "#datasetId")
    public RunResult run(
            @PathVariable long datasetId,
            @RequestBody RunRequest request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return qualityService.run(
                datasetId,
                request.ruleIds(),
                userId(jwt),
                isAdmin(authentication)
        );
    }

    @GetMapping("/logs")
    @PreAuthorize("hasAuthority('data:qc:view')")
    public PageResponse<ExecutionView> logs(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long datasetId,
            @RequestParam(required = false) Long ruleId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean effectivePass,
            @RequestParam(required = false) Boolean overridden,
            @RequestParam(required = false) String keyword,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return qualityService.logs(
                page,
                size,
                new LogFilter(
                        projectId,
                        datasetId,
                        ruleId,
                        status,
                        effectivePass,
                        overridden,
                        keyword
                ),
                userId(jwt),
                isAdmin(authentication)
        );
    }

    @GetMapping("/executions/{id}")
    @PreAuthorize("hasAuthority('data:qc:view')")
    public ExecutionDetail execution(
            @PathVariable long id,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return qualityService.execution(id, userId(jwt), isAdmin(authentication));
    }

    @PostMapping("/executions/{id}/complete")
    @PreAuthorize("hasAuthority('data:qc:execute')")
    @Audited(module = "DATA_QC", action = "COMPLETE", targetType = "QC_EXECUTION",
            targetId = "#id")
    public ExecutionDetail complete(
            @PathVariable long id,
            @Valid @RequestBody ReportRequest request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return qualityService.complete(id, request, userId(jwt), isAdmin(authentication));
    }

    @PutMapping("/executions/{id}/override")
    @PreAuthorize("hasAuthority('data:qc:override')")
    @Audited(module = "DATA_QC", action = "OVERRIDE", targetType = "QC_EXECUTION",
            targetId = "#id")
    public ExecutionView override(
            @PathVariable long id,
            @Valid @RequestBody OverrideRequest request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return qualityService.override(id, request, userId(jwt), isAdmin(authentication));
    }

    private long userId(Jwt jwt) {
        return ((Number) jwt.getClaim("uid")).longValue();
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> GLOBAL_PERMISSION.equals(authority.getAuthority()));
    }
}

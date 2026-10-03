package com.dayan.platform.controller;

import com.dayan.platform.audit.Audited;
import com.dayan.platform.dto.WorkflowDtos.ActionRuleRequest;
import com.dayan.platform.dto.WorkflowDtos.DefinitionRequest;
import com.dayan.platform.dto.WorkflowDtos.MatchRuleRequest;
import com.dayan.platform.dto.WorkflowDtos.RunRequest;
import com.dayan.platform.dto.WorkflowDtos.TestRequest;
import com.dayan.platform.service.WorkflowService;
import com.dayan.platform.vo.WorkflowViews.ActionRuleView;
import com.dayan.platform.vo.WorkflowViews.DatasetOption;
import com.dayan.platform.vo.WorkflowViews.DefinitionView;
import com.dayan.platform.vo.WorkflowViews.MatchRuleView;
import com.dayan.platform.vo.WorkflowViews.Overview;
import com.dayan.platform.vo.WorkflowViews.ProjectOption;
import com.dayan.platform.vo.WorkflowViews.RunView;
import com.dayan.platform.vo.WorkflowViews.TestResult;
import jakarta.validation.Valid;
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
@RequestMapping("${app.api.base-path}/workflows")
public class WorkflowController {

    private static final String GLOBAL_PERMISSION = "basic:workflow:global";

    private final WorkflowService workflowService;

    public WorkflowController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('basic:workflow:view')")
    public Overview overview(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return workflowService.overview(userId(jwt), isAdmin(authentication));
    }

    @GetMapping("/match-rules")
    @PreAuthorize("hasAuthority('basic:workflow:view')")
    public List<MatchRuleView> matchRules(
            @RequestParam(required = false) @Positive Long projectId,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return workflowService.matchRules(
                projectId, enabled, keyword, userId(jwt), isAdmin(authentication)
        );
    }

    @PostMapping("/match-rules")
    @PreAuthorize("hasAuthority('basic:workflow:manage')")
    @Audited(module = "WORKFLOW", action = "CREATE_MATCH_RULE",
            targetType = "WORKFLOW_MATCH_RULE", targetId = "#result.id()")
    public MatchRuleView createMatchRule(
            @Valid @RequestBody MatchRuleRequest request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return workflowService.saveMatchRule(
                null, request, userId(jwt), isAdmin(authentication)
        );
    }

    @PutMapping("/match-rules/{id}")
    @PreAuthorize("hasAuthority('basic:workflow:manage')")
    @Audited(module = "WORKFLOW", action = "UPDATE_MATCH_RULE",
            targetType = "WORKFLOW_MATCH_RULE", targetId = "#id")
    public MatchRuleView updateMatchRule(
            @PathVariable long id,
            @Valid @RequestBody MatchRuleRequest request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return workflowService.saveMatchRule(
                id, request, userId(jwt), isAdmin(authentication)
        );
    }

    @PostMapping("/match-rules/{id}/test")
    @PreAuthorize("hasAuthority('basic:workflow:test')")
    public TestResult testMatchRule(
            @PathVariable long id,
            @Valid @RequestBody TestRequest request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return workflowService.testMatchRule(
                id, request, userId(jwt), isAdmin(authentication)
        );
    }

    @GetMapping("/action-rules")
    @PreAuthorize("hasAuthority('basic:workflow:view')")
    public List<ActionRuleView> actionRules(
            @RequestParam(required = false) @Positive Long projectId,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return workflowService.actionRules(
                projectId, enabled, keyword, userId(jwt), isAdmin(authentication)
        );
    }

    @PostMapping("/action-rules")
    @PreAuthorize("hasAuthority('basic:workflow:manage')")
    @Audited(module = "WORKFLOW", action = "CREATE_ACTION_RULE",
            targetType = "WORKFLOW_ACTION_RULE", targetId = "#result.id()")
    public ActionRuleView createActionRule(
            @Valid @RequestBody ActionRuleRequest request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return workflowService.saveActionRule(
                null, request, userId(jwt), isAdmin(authentication)
        );
    }

    @PutMapping("/action-rules/{id}")
    @PreAuthorize("hasAuthority('basic:workflow:manage')")
    @Audited(module = "WORKFLOW", action = "UPDATE_ACTION_RULE",
            targetType = "WORKFLOW_ACTION_RULE", targetId = "#id")
    public ActionRuleView updateActionRule(
            @PathVariable long id,
            @Valid @RequestBody ActionRuleRequest request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return workflowService.saveActionRule(
                id, request, userId(jwt), isAdmin(authentication)
        );
    }

    @GetMapping("/definitions")
    @PreAuthorize("hasAuthority('basic:workflow:view')")
    public List<DefinitionView> definitions(
            @RequestParam(required = false) @Positive Long projectId,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String keyword,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return workflowService.definitions(
                projectId, enabled, keyword, userId(jwt), isAdmin(authentication)
        );
    }

    @PostMapping("/definitions")
    @PreAuthorize("hasAuthority('basic:workflow:manage')")
    @Audited(module = "WORKFLOW", action = "CREATE_WORKFLOW",
            targetType = "WORKFLOW", targetId = "#result.id()")
    public DefinitionView createDefinition(
            @Valid @RequestBody DefinitionRequest request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return workflowService.saveDefinition(
                null, request, userId(jwt), isAdmin(authentication)
        );
    }

    @PutMapping("/definitions/{id}")
    @PreAuthorize("hasAuthority('basic:workflow:manage')")
    @Audited(module = "WORKFLOW", action = "UPDATE_WORKFLOW",
            targetType = "WORKFLOW", targetId = "#id")
    public DefinitionView updateDefinition(
            @PathVariable long id,
            @Valid @RequestBody DefinitionRequest request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return workflowService.saveDefinition(
                id, request, userId(jwt), isAdmin(authentication)
        );
    }

    @DeleteMapping("/{resource}/{id}")
    @PreAuthorize("hasAuthority('basic:workflow:manage')")
    @Audited(module = "WORKFLOW", action = "DELETE", targetType = "WORKFLOW_RESOURCE",
            targetId = "#id")
    public void delete(
            @PathVariable String resource,
            @PathVariable long id,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        workflowService.delete(resource, id, userId(jwt), isAdmin(authentication));
    }

    @GetMapping("/projects")
    @PreAuthorize("hasAuthority('basic:workflow:view')")
    public List<ProjectOption> projects(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return workflowService.projects(userId(jwt), isAdmin(authentication));
    }

    @GetMapping("/datasets")
    @PreAuthorize("hasAuthority('basic:workflow:view')")
    public List<DatasetOption> datasets(
            @RequestParam(required = false) @Positive Long projectId,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return workflowService.datasets(projectId, userId(jwt), isAdmin(authentication));
    }

    @GetMapping("/definitions/{id}/datasets")
    @PreAuthorize("hasAuthority('basic:workflow:view')")
    public List<DatasetOption> matchingDatasets(
            @PathVariable @Positive long id,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return workflowService.matchingDatasets(id, userId(jwt), isAdmin(authentication));
    }

    @GetMapping("/runs")
    @PreAuthorize("hasAuthority('basic:workflow:view')")
    public List<RunView> runs(
            @RequestParam(required = false) @Positive Long projectId,
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return workflowService.runs(
                projectId, status, userId(jwt), isAdmin(authentication)
        );
    }

    @PostMapping("/runs")
    @PreAuthorize("hasAuthority('basic:workflow:test')")
    @Audited(module = "WORKFLOW", action = "TEST_RUN",
            targetType = "WORKFLOW_RUN", targetId = "#result.id()")
    public RunView startRun(
            @Valid @RequestBody RunRequest request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return workflowService.startRun(
                request.workflowId(),
                request.datasetId(),
                userId(jwt),
                isAdmin(authentication)
        );
    }

    private long userId(Jwt jwt) {
        return ((Number) jwt.getClaim("uid")).longValue();
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> GLOBAL_PERMISSION.equals(authority.getAuthority()));
    }
}

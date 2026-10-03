package com.dayan.platform.controller;

import com.dayan.platform.service.ChartService;
import com.dayan.platform.vo.ChartViews.CalendarData;
import com.dayan.platform.vo.ChartViews.DurationPoint;
import com.dayan.platform.vo.ChartViews.GraphData;
import com.dayan.platform.vo.ChartViews.HierarchyNode;
import com.dayan.platform.vo.ChartViews.ProjectOption;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Set;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("${app.api.base-path}/data/charts")
@PreAuthorize("hasAuthority('data:chart:view') and hasAuthority('basic:project:view')")
public class ChartController {

    private static final Set<String> PLATFORM_ADMIN_PERMISSIONS = Set.of(
            "basic:project:create",
            "basic:project:update",
            "basic:project:delete",
            "basic:project:member:manage"
    );

    private final ChartService chartService;

    public ChartController(ChartService chartService) {
        this.chartService = chartService;
    }

    @GetMapping("/projects")
    public List<ProjectOption> projects(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return chartService.projectOptions(userId(jwt), isPlatformAdmin(authentication));
    }

    @GetMapping("/relationships")
    public List<HierarchyNode> relationships(
            @RequestParam @Positive long projectId,
            @RequestParam(defaultValue = "en-US") String locale,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return chartService.relationships(
                projectId,
                locale,
                userId(jwt),
                isPlatformAdmin(authentication)
        );
    }

    @GetMapping("/planning")
    public GraphData planning(
            @RequestParam @Positive long projectId,
            @RequestParam(defaultValue = "en-US") String locale,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return chartService.planning(
                projectId,
                locale,
                userId(jwt),
                isPlatformAdmin(authentication)
        );
    }

    @GetMapping("/durations")
    public List<DurationPoint> durations(
            @RequestParam @Positive long projectId,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return chartService.durations(projectId, userId(jwt), isPlatformAdmin(authentication));
    }

    @GetMapping("/dependencies")
    public GraphData dependencies(
            @RequestParam @Positive long projectId,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return chartService.dependencies(projectId, userId(jwt), isPlatformAdmin(authentication));
    }

    @GetMapping("/calendar")
    public CalendarData calendar(
            @RequestParam @Positive long projectId,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return chartService.calendar(projectId, userId(jwt), isPlatformAdmin(authentication));
    }

    private long userId(Jwt jwt) {
        return ((Number) jwt.getClaim("uid")).longValue();
    }

    private boolean isPlatformAdmin(Authentication authentication) {
        Set<String> permissions = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .collect(java.util.stream.Collectors.toSet());
        return permissions.containsAll(PLATFORM_ADMIN_PERMISSIONS);
    }
}

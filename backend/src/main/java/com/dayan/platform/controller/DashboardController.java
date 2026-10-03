package com.dayan.platform.controller;

import com.dayan.platform.service.DashboardService;
import com.dayan.platform.service.DashboardService.DashboardAccess;
import com.dayan.platform.vo.DashboardView;
import java.util.Set;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${app.api.base-path}/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping({"", "/statistics"})
    @PreAuthorize("hasAuthority('dashboard:view')")
    public DashboardView statistics(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        Set<String> authorities = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .collect(java.util.stream.Collectors.toSet());
        boolean platformAdmin = authorities.containsAll(Set.of(
                "basic:project:create",
                "basic:project:update",
                "basic:project:delete",
                "basic:project:member:manage"
        ));
        DashboardAccess access = new DashboardAccess(
                platformAdmin,
                platformAdmin || authorities.contains("data:manage:view"),
                platformAdmin || authorities.contains("basic:project:view"),
                platformAdmin || authorities.contains("data:collect:task:view"),
                platformAdmin || authorities.contains("data:collect:task:submit"),
                platformAdmin || authorities.contains("data:qc:view")
        );
        return dashboardService.statistics(
                ((Number) jwt.getClaim("uid")).longValue(),
                access
        );
    }
}

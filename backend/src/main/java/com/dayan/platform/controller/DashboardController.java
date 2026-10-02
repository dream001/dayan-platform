package com.dayan.platform.controller;

import com.dayan.platform.service.DashboardService;
import com.dayan.platform.vo.DashboardView;
import org.springframework.security.access.prepost.PreAuthorize;
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
    public DashboardView statistics() {
        return dashboardService.statistics();
    }
}

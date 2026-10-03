package com.dayan.platform.controller;

import com.dayan.platform.audit.Audited;
import com.dayan.platform.dto.RobotDtos.RobotRequest;
import com.dayan.platform.dto.RobotDtos.RobotType;
import com.dayan.platform.service.RobotService;
import com.dayan.platform.vo.RobotViews.RobotDataset;
import com.dayan.platform.vo.RobotViews.RobotSummary;
import jakarta.validation.Valid;
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
@RequestMapping("${app.api.base-path}/basic/robots")
public class RobotController {

    private static final String ADMIN_PERMISSION = "basic:project:update";

    private final RobotService robotService;

    public RobotController(RobotService robotService) {
        this.robotService = robotService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('basic:robot:view')")
    public List<RobotSummary> list(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @RequestParam(required = false) RobotType robotType
    ) {
        return robotService.list(robotType, userId(jwt), isAdmin(authentication));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('basic:robot:view')")
    @Audited(
            module = "ROBOT",
            action = "CREATE",
            targetType = "ROBOT",
            targetId = "#result == null ? #request.name() : #result.id()"
    )
    public RobotSummary create(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @Valid @RequestBody RobotRequest request
    ) {
        return robotService.create(request, userId(jwt), isAdmin(authentication));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('basic:robot:view')")
    @Audited(module = "ROBOT", action = "UPDATE", targetType = "ROBOT", targetId = "#id")
    public RobotSummary update(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @PathVariable long id,
            @Valid @RequestBody RobotRequest request
    ) {
        return robotService.update(id, request, userId(jwt), isAdmin(authentication));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('basic:robot:view')")
    @Audited(module = "ROBOT", action = "DELETE", targetType = "ROBOT", targetId = "#id")
    public void delete(
            Authentication authentication,
            @PathVariable long id
    ) {
        robotService.delete(id, isAdmin(authentication));
    }

    @GetMapping("/{id}/datasets")
    @PreAuthorize("hasAuthority('basic:robot:view')")
    public List<RobotDataset> datasets(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @PathVariable long id
    ) {
        return robotService.datasets(id, userId(jwt), isAdmin(authentication));
    }

    private long userId(Jwt jwt) {
        return ((Number) jwt.getClaim("uid")).longValue();
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> ADMIN_PERMISSION.equals(authority.getAuthority()));
    }
}

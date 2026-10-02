package com.dayan.platform.controller;

import com.dayan.platform.audit.Audited;
import com.dayan.platform.dto.ProjectDtos.ProjectMemberRequest;
import com.dayan.platform.dto.ProjectDtos.ProjectRequest;
import com.dayan.platform.dto.ProjectDtos.ProjectStatusRequest;
import com.dayan.platform.service.ProjectService;
import com.dayan.platform.vo.PageResponse;
import com.dayan.platform.vo.ProjectViews.ProjectDetail;
import com.dayan.platform.vo.ProjectViews.ProjectMember;
import com.dayan.platform.vo.ProjectViews.ProjectOverview;
import com.dayan.platform.vo.ProjectViews.ProjectSummary;
import com.dayan.platform.vo.ProjectViews.ProjectUserOption;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("${app.api.base-path}/basic/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('basic:project:view')")
    public PageResponse<ProjectSummary> page(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String projectType
    ) {
        return projectService.page(
                page,
                size,
                keyword,
                status,
                projectType,
                userId(jwt),
                isPlatformAdmin(authentication)
        );
    }

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('basic:project:view')")
    public ProjectOverview overview(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return projectService.overview(userId(jwt), isPlatformAdmin(authentication));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('basic:project:view')")
    public ProjectDetail detail(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @PathVariable long id
    ) {
        return projectService.detail(id, userId(jwt), isPlatformAdmin(authentication));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('basic:project:create')")
    @Audited(
            module = "PROJECT",
            action = "CREATE",
            targetType = "PROJECT",
            targetId = "#result == null ? #request.code() : #result.summary().id()"
    )
    public ProjectDetail create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ProjectRequest request
    ) {
        return projectService.create(request, userId(jwt));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('basic:project:view')")
    @Audited(module = "PROJECT", action = "UPDATE", targetType = "PROJECT", targetId = "#id")
    public ProjectDetail update(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @PathVariable long id,
            @Valid @RequestBody ProjectRequest request
    ) {
        return projectService.update(id, request, userId(jwt), isPlatformAdmin(authentication));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('basic:project:view')")
    @Audited(module = "PROJECT", action = "CHANGE_STATUS", targetType = "PROJECT", targetId = "#id")
    public ProjectDetail changeStatus(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @PathVariable long id,
            @Valid @RequestBody ProjectStatusRequest request
    ) {
        return projectService.changeStatus(
                id,
                request.status(),
                userId(jwt),
                isPlatformAdmin(authentication)
        );
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('basic:project:view')")
    @Audited(module = "PROJECT", action = "DELETE", targetType = "PROJECT", targetId = "#id")
    public void delete(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @PathVariable long id
    ) {
        projectService.delete(id, userId(jwt), isPlatformAdmin(authentication));
    }

    @GetMapping("/{id}/members")
    @PreAuthorize("hasAuthority('basic:project:view')")
    public List<ProjectMember> members(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @PathVariable long id
    ) {
        return projectService.members(id, userId(jwt), isPlatformAdmin(authentication));
    }

    @PutMapping("/{id}/members")
    @PreAuthorize("hasAuthority('basic:project:view')")
    @Audited(module = "PROJECT", action = "SAVE_MEMBER", targetType = "PROJECT", targetId = "#id")
    public ProjectMember saveMember(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @PathVariable long id,
            @Valid @RequestBody ProjectMemberRequest request
    ) {
        return projectService.saveMember(
                id,
                request,
                userId(jwt),
                isPlatformAdmin(authentication)
        );
    }

    @DeleteMapping("/{id}/members/{userId}")
    @PreAuthorize("hasAuthority('basic:project:view')")
    @Audited(module = "PROJECT", action = "REMOVE_MEMBER", targetType = "PROJECT", targetId = "#id")
    public void removeMember(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @PathVariable long id,
            @PathVariable long userId
    ) {
        projectService.removeMember(
                id,
                userId,
                userId(jwt),
                isPlatformAdmin(authentication)
        );
    }

    @GetMapping("/{id}/user-options")
    @PreAuthorize("hasAuthority('basic:project:view')")
    public List<ProjectUserOption> userOptions(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @PathVariable long id,
            @RequestParam(required = false) String keyword
    ) {
        return projectService.userOptions(
                id,
                keyword,
                userId(jwt),
                isPlatformAdmin(authentication)
        );
    }

    private long userId(Jwt jwt) {
        return ((Number) jwt.getClaim("uid")).longValue();
    }

    private boolean isPlatformAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .collect(java.util.stream.Collectors.toSet())
                .containsAll(List.of(
                        "basic:project:create",
                        "basic:project:update",
                        "basic:project:delete",
                        "basic:project:member:manage"
                ));
    }
}

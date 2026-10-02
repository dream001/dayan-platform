package com.dayan.platform.controller;

import com.dayan.platform.audit.Audited;
import com.dayan.platform.dto.RbacDtos.IdSetRequest;
import com.dayan.platform.dto.RbacDtos.RoleRequest;
import com.dayan.platform.service.RoleService;
import com.dayan.platform.vo.PageResponse;
import com.dayan.platform.vo.RbacViews.RoleDetail;
import com.dayan.platform.vo.RbacViews.RoleSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("${app.api.base-path}/system/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('system:role:view')")
    public PageResponse<RoleSummary> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean enabled
    ) {
        return roleService.page(page, size, keyword, enabled);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role:view')")
    public RoleDetail detail(@PathVariable long id) {
        return roleService.detail(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:role:create')")
    @Audited(
            module = "ROLE",
            action = "CREATE",
            targetType = "ROLE",
            targetId = "#result == null ? #request.code() : #result.role().id()"
    )
    public RoleDetail create(@Valid @RequestBody RoleRequest request) {
        return roleService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role:update')")
    @Audited(module = "ROLE", action = "UPDATE", targetType = "ROLE", targetId = "#id")
    public RoleDetail update(
            @PathVariable long id,
            @Valid @RequestBody RoleRequest request
    ) {
        return roleService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:role:delete')")
    @Audited(module = "ROLE", action = "DELETE", targetType = "ROLE", targetId = "#id")
    public void delete(@PathVariable long id) {
        roleService.delete(id);
    }

    @PutMapping("/{id}/users")
    @PreAuthorize("hasAuthority('system:role:grant')")
    @Audited(module = "ROLE", action = "ASSIGN_USERS", targetType = "ROLE", targetId = "#id")
    public void assignUsers(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long id,
            @Valid @RequestBody IdSetRequest request
    ) {
        roleService.assignUsers(id, request.ids(), userId(jwt));
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('system:role:grant')")
    @Audited(module = "ROLE", action = "GRANT_PERMISSIONS", targetType = "ROLE", targetId = "#id")
    public void grantPermissions(
            @PathVariable long id,
            @Valid @RequestBody IdSetRequest request
    ) {
        roleService.grantPermissions(id, request.ids());
    }

    private long userId(Jwt jwt) {
        return ((Number) jwt.getClaim("uid")).longValue();
    }
}

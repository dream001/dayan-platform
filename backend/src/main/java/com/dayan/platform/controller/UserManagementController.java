package com.dayan.platform.controller;

import com.dayan.platform.audit.Audited;
import com.dayan.platform.dto.RbacDtos.BatchUserCreateRequest;
import com.dayan.platform.dto.RbacDtos.IdSetRequest;
import com.dayan.platform.dto.RbacDtos.PasswordResetRequest;
import com.dayan.platform.dto.RbacDtos.StatusRequest;
import com.dayan.platform.dto.RbacDtos.UserCreateRequest;
import com.dayan.platform.dto.RbacDtos.UserUpdateRequest;
import com.dayan.platform.service.UserManagementService;
import com.dayan.platform.vo.PageResponse;
import com.dayan.platform.vo.RbacViews.UserFilterOptions;
import com.dayan.platform.vo.RbacViews.UserRoleCounts;
import com.dayan.platform.vo.RbacViews.UserSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("${app.api.base-path}/system/users")
public class UserManagementController {

    private final UserManagementService userManagementService;

    public UserManagementController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('system:user:view')")
    public PageResponse<UserSummary> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) @Size(max = 64) String roleCode,
            @RequestParam(required = false) @Min(1) Long projectId
    ) {
        return userManagementService.page(
                page,
                size,
                keyword,
                departmentId,
                enabled,
                roleCode,
                projectId
        );
    }

    @GetMapping("/filter-options")
    @PreAuthorize("hasAuthority('system:user:view')")
    public UserFilterOptions filterOptions() {
        return userManagementService.filterOptions();
    }

    @GetMapping("/role-counts")
    @PreAuthorize("hasAuthority('system:user:view')")
    public UserRoleCounts roleCounts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) @Min(1) Long projectId
    ) {
        return userManagementService.roleCounts(keyword, departmentId, enabled, projectId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:view')")
    public UserSummary detail(@PathVariable long id) {
        return userManagementService.detail(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:user:create')")
    @Audited(
            module = "USER",
            action = "CREATE",
            targetType = "USER",
            targetId = "#result == null ? #request.username() : #result.id()"
    )
    public UserSummary create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UserCreateRequest request
    ) {
        return userManagementService.create(request, userId(jwt));
    }

    @PostMapping("/batch")
    @PreAuthorize("hasAuthority('system:user:create')")
    @Audited(module = "USER", action = "BATCH_CREATE", targetType = "USER_BATCH")
    public List<UserSummary> createBatch(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody BatchUserCreateRequest request
    ) {
        return userManagementService.createBatch(request, userId(jwt));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:update')")
    @Audited(module = "USER", action = "UPDATE", targetType = "USER", targetId = "#id")
    public UserSummary update(
            @PathVariable long id,
            @Valid @RequestBody UserUpdateRequest request
    ) {
        return userManagementService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('system:user:change-status')")
    @Audited(module = "USER", action = "CHANGE_STATUS", targetType = "USER", targetId = "#id")
    public void changeStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long id,
            @Valid @RequestBody StatusRequest request
    ) {
        userManagementService.changeStatus(id, request.enabled(), userId(jwt));
    }

    @PutMapping("/{id}/password")
    @PreAuthorize("hasAuthority('system:user:reset-password')")
    @Audited(module = "USER", action = "RESET_PASSWORD", targetType = "USER", targetId = "#id")
    public void resetPassword(
            @PathVariable long id,
            @Valid @RequestBody PasswordResetRequest request
    ) {
        userManagementService.resetPassword(id, request.newPassword());
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('system:user:assign-role')")
    @Audited(module = "USER", action = "ASSIGN_ROLES", targetType = "USER", targetId = "#id")
    public void assignRoles(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long id,
            @Valid @RequestBody IdSetRequest request
    ) {
        userManagementService.assignRoles(id, request.ids(), userId(jwt));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:user:delete')")
    @Audited(module = "USER", action = "DELETE", targetType = "USER", targetId = "#id")
    public void delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable long id
    ) {
        userManagementService.delete(id, userId(jwt));
    }

    private long userId(Jwt jwt) {
        return ((Number) jwt.getClaim("uid")).longValue();
    }
}

package com.dayan.platform.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

public final class RbacDtos {

    private RbacDtos() {
    }

    public record DepartmentRequest(
            Long parentId,
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 64) String code,
            @NotNull Integer sortOrder,
            @NotNull Boolean enabled
    ) {
    }

    public record UserCreateRequest(
            Long departmentId,
            @NotBlank @Size(max = 64)
            @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "username format is invalid")
            String username,
            @NotBlank @Size(min = 12, max = 72) String password,
            @NotBlank @Size(max = 100) String displayName,
            @Email @Size(max = 254) String email,
            @Pattern(regexp = "^$|^[0-9+() -]{3,32}$", message = "phone format is invalid")
            String phone,
            @NotNull Boolean enabled,
            Set<Long> roleIds
    ) {
    }

    public record UserUpdateRequest(
            Long departmentId,
            @NotBlank @Size(max = 100) String displayName,
            @Email @Size(max = 254) String email,
            @Pattern(regexp = "^$|^[0-9+() -]{3,32}$", message = "phone format is invalid")
            String phone
    ) {
    }

    public record StatusRequest(@NotNull Boolean enabled) {
    }

    public record PasswordResetRequest(@NotBlank @Size(min = 12, max = 72) String newPassword) {
    }

    public record IdSetRequest(@NotNull Set<@NotNull Long> ids) {
    }

    public record RoleRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 64)
            @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]*$", message = "role code format is invalid")
            String code,
            @Size(max = 500) String description,
            @NotNull Boolean enabled
    ) {
    }

    public record MenuRequest(
            Long parentId,
            @NotBlank @Pattern(regexp = "MENU|BUTTON") String type,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 100) String code,
            @Size(max = 255) String path,
            @Size(max = 255) String component,
            @Size(max = 100) String icon,
            @NotNull Integer sortOrder,
            @NotNull Boolean visible,
            @NotNull Boolean enabled
    ) {
    }
}

package com.dayan.platform.vo;

import java.time.OffsetDateTime;
import java.util.List;

public final class RbacViews {

    private RbacViews() {
    }

    public record DepartmentNode(
            long id,
            Long parentId,
            String name,
            String code,
            int sortOrder,
            boolean enabled,
            List<DepartmentNode> children
    ) {
        public DepartmentNode {
            children = List.copyOf(children);
        }
    }

    public record RoleBrief(long id, String name, String code, boolean enabled) {
    }

    public record UserSummary(
            long id,
            Long departmentId,
            String departmentName,
            String username,
            String displayName,
            String email,
            String phone,
            boolean enabled,
            OffsetDateTime lastLoginAt,
            OffsetDateTime createdAt,
            List<RoleBrief> roles
    ) {
        public UserSummary {
            roles = List.copyOf(roles);
        }
    }

    public record UserProjectOption(long id, String name) {
    }

    public record UserFilterOptions(List<UserProjectOption> projects) {
        public UserFilterOptions {
            projects = List.copyOf(projects);
        }
    }

    public record RoleSummary(
            long id,
            String name,
            String code,
            String description,
            boolean enabled,
            boolean builtIn,
            long userCount,
            OffsetDateTime createdAt
    ) {
    }

    public record RoleDetail(RoleSummary role, List<Long> userIds, List<Long> permissionIds) {
        public RoleDetail {
            userIds = List.copyOf(userIds);
            permissionIds = List.copyOf(permissionIds);
        }
    }

    public record MenuNode(
            long id,
            Long parentId,
            String type,
            String name,
            String code,
            String path,
            String component,
            String icon,
            int sortOrder,
            boolean visible,
            boolean enabled,
            List<MenuNode> children
    ) {
        public MenuNode {
            children = List.copyOf(children);
        }
    }
}

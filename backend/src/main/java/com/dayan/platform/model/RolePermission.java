package com.dayan.platform.model;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

@TableName("sys_role_permission")
public class RolePermission {

    private Long roleId;
    private Long permissionId;
    private OffsetDateTime grantedAt;

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public Long getPermissionId() {
        return permissionId;
    }

    public void setPermissionId(Long permissionId) {
        this.permissionId = permissionId;
    }

    public OffsetDateTime getGrantedAt() {
        return grantedAt;
    }

    public void setGrantedAt(OffsetDateTime grantedAt) {
        this.grantedAt = grantedAt;
    }
}

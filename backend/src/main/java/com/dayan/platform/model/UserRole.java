package com.dayan.platform.model;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

@TableName("sys_user_role")
public class UserRole {

    private Long userId;
    private Long roleId;
    private OffsetDateTime assignedAt;
    private Long assignedBy;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public OffsetDateTime getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(OffsetDateTime assignedAt) {
        this.assignedAt = assignedAt;
    }

    public Long getAssignedBy() {
        return assignedBy;
    }

    public void setAssignedBy(Long assignedBy) {
        this.assignedBy = assignedBy;
    }
}

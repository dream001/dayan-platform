package com.dayan.platform.service.impl;

import com.dayan.platform.common.api.ErrorCode;
import com.dayan.platform.common.exception.BusinessException;
import com.dayan.platform.dto.RbacDtos.RoleRequest;
import com.dayan.platform.model.Role;
import com.dayan.platform.repository.mapper.MenuPermissionMapper;
import com.dayan.platform.repository.mapper.RoleMapper;
import com.dayan.platform.repository.mapper.RolePermissionMapper;
import com.dayan.platform.repository.mapper.UserAccountMapper;
import com.dayan.platform.repository.mapper.UserRoleMapper;
import com.dayan.platform.repository.query.RoleSummaryRow;
import com.dayan.platform.service.RoleService;
import com.dayan.platform.vo.PageResponse;
import com.dayan.platform.vo.RbacViews.RoleDetail;
import com.dayan.platform.vo.RbacViews.RoleSummary;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class RoleServiceImpl implements RoleService {

    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final UserAccountMapper userAccountMapper;
    private final MenuPermissionMapper menuPermissionMapper;

    public RoleServiceImpl(
            RoleMapper roleMapper,
            UserRoleMapper userRoleMapper,
            RolePermissionMapper rolePermissionMapper,
            UserAccountMapper userAccountMapper,
            MenuPermissionMapper menuPermissionMapper
    ) {
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.userAccountMapper = userAccountMapper;
        this.menuPermissionMapper = menuPermissionMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RoleSummary> page(
            int page,
            int size,
            String keyword,
            Boolean enabled
    ) {
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        long total = roleMapper.countSummaries(normalizedKeyword, enabled);
        List<RoleSummary> items = roleMapper.selectSummaryPage(
                normalizedKeyword,
                enabled,
                (long) (page - 1) * size,
                size
        ).stream().map(this::summary).toList();
        return PageResponse.of(page, size, total, items);
    }

    @Override
    @Transactional(readOnly = true)
    public RoleDetail detail(long id) {
        RoleSummaryRow row = roleMapper.selectSummaryById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Role not found");
        }
        return new RoleDetail(
                summary(row),
                userRoleMapper.selectUserIdsByRoleId(id),
                rolePermissionMapper.selectPermissionIdsByRoleId(id)
        );
    }

    @Override
    @Transactional
    public RoleDetail create(RoleRequest request) {
        Role role = new Role();
        apply(role, request);
        role.setBuiltIn(false);
        try {
            roleMapper.insert(role);
        } catch (DataIntegrityViolationException exception) {
            throw conflict("Role name or code already exists");
        }
        return detail(role.getId());
    }

    @Override
    @Transactional
    public RoleDetail update(long id, RoleRequest request) {
        Role role = requireRole(id);
        String requestedCode = request.code().trim().toUpperCase(Locale.ROOT);
        if (Boolean.TRUE.equals(role.getBuiltIn()) && !role.getCode().equals(requestedCode)) {
            throw conflict("Built-in role code cannot be changed");
        }
        if (Boolean.TRUE.equals(role.getBuiltIn()) && !request.enabled()) {
            throw conflict("Built-in role cannot be disabled");
        }
        apply(role, request);
        role.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        try {
            roleMapper.updateById(role);
        } catch (DataIntegrityViolationException exception) {
            throw conflict("Role name or code already exists");
        }
        return detail(id);
    }

    @Override
    @Transactional
    public void delete(long id) {
        Role role = requireRole(id);
        if (Boolean.TRUE.equals(role.getBuiltIn())) {
            throw conflict("Built-in role cannot be deleted");
        }
        if (userRoleMapper.countByRoleId(id) > 0) {
            throw conflict("Role is still assigned to users");
        }
        roleMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void assignUsers(long id, Set<Long> userIds, long operatorId) {
        Role role = requireRole(id);
        Set<Long> normalized = normalizedIds(userIds);
        if (!normalized.isEmpty() && userAccountMapper.countByIds(normalized) != normalized.size()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "One or more users do not exist");
        }
        if (Boolean.TRUE.equals(role.getBuiltIn()) && normalized.isEmpty()) {
            throw conflict("Built-in role must retain at least one user");
        }
        userRoleMapper.deleteByRoleId(id);
        if (!normalized.isEmpty()) {
            userRoleMapper.insertUsers(id, normalized, operatorId);
        }
    }

    @Override
    @Transactional
    public void grantPermissions(long id, Set<Long> permissionIds) {
        Role role = requireRole(id);
        Set<Long> normalized = normalizedIds(permissionIds);
        if (!normalized.isEmpty() && menuPermissionMapper.countByIds(normalized) != normalized.size()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "One or more permissions do not exist");
        }
        if (Boolean.TRUE.equals(role.getBuiltIn()) && normalized.isEmpty()) {
            throw conflict("Built-in role permissions cannot be empty");
        }
        rolePermissionMapper.deleteByRoleId(id);
        if (!normalized.isEmpty()) {
            rolePermissionMapper.insertPermissions(id, normalized);
        }
    }

    private Role requireRole(long id) {
        Role role = roleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Role not found");
        }
        return role;
    }

    private void apply(Role role, RoleRequest request) {
        role.setName(request.name().trim());
        role.setCode(request.code().trim().toUpperCase(Locale.ROOT));
        role.setDescription(StringUtils.hasText(request.description()) ? request.description().trim() : null);
        role.setEnabled(request.enabled());
    }

    private RoleSummary summary(RoleSummaryRow row) {
        return new RoleSummary(
                row.getId(),
                row.getName(),
                row.getCode(),
                row.getDescription(),
                Boolean.TRUE.equals(row.getEnabled()),
                Boolean.TRUE.equals(row.getBuiltIn()),
                row.getUserCount(),
                row.getCreatedAt()
        );
    }

    private Set<Long> normalizedIds(Set<Long> ids) {
        return ids == null ? Set.of() : new LinkedHashSet<>(ids);
    }

    private BusinessException conflict(String message) {
        return new BusinessException(ErrorCode.CONFLICT, message);
    }
}

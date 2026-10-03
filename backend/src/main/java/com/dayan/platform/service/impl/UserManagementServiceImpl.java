package com.dayan.platform.service.impl;

import com.dayan.platform.common.api.ErrorCode;
import com.dayan.platform.common.exception.BusinessException;
import com.dayan.platform.dto.RbacDtos.BatchUserCreateRequest;
import com.dayan.platform.dto.RbacDtos.UserCreateRequest;
import com.dayan.platform.dto.RbacDtos.UserUpdateRequest;
import com.dayan.platform.model.UserAccount;
import com.dayan.platform.repository.mapper.AuthSessionMapper;
import com.dayan.platform.repository.mapper.DepartmentMapper;
import com.dayan.platform.repository.mapper.RoleMapper;
import com.dayan.platform.repository.mapper.UserAccountMapper;
import com.dayan.platform.repository.mapper.UserRoleMapper;
import com.dayan.platform.repository.query.OptionRow;
import com.dayan.platform.repository.query.UserSummaryRow;
import com.dayan.platform.service.UserManagementService;
import com.dayan.platform.vo.PageResponse;
import com.dayan.platform.vo.RbacViews.RoleBrief;
import com.dayan.platform.vo.RbacViews.UserFilterOptions;
import com.dayan.platform.vo.RbacViews.UserProjectOption;
import com.dayan.platform.vo.RbacViews.UserSummary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class UserManagementServiceImpl implements UserManagementService {

    private final UserAccountMapper userAccountMapper;
    private final DepartmentMapper departmentMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final AuthSessionMapper authSessionMapper;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    public UserManagementServiceImpl(
            UserAccountMapper userAccountMapper,
            DepartmentMapper departmentMapper,
            RoleMapper roleMapper,
            UserRoleMapper userRoleMapper,
            AuthSessionMapper authSessionMapper,
            PasswordEncoder passwordEncoder,
            ObjectMapper objectMapper
    ) {
        this.userAccountMapper = userAccountMapper;
        this.departmentMapper = departmentMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.authSessionMapper = authSessionMapper;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserSummary> page(
            int page,
            int size,
            String keyword,
            Long departmentId,
            Boolean enabled,
            String roleCode,
            Long projectId
    ) {
        String normalizedKeyword = normalizeNullable(keyword, false);
        String normalizedRoleCode = normalizeEnum(roleCode);
        long total = userAccountMapper.countSummaries(
                normalizedKeyword,
                departmentId,
                enabled,
                normalizedRoleCode,
                projectId
        );
        List<UserSummary> items = userAccountMapper.selectSummaryPage(
                normalizedKeyword,
                departmentId,
                enabled,
                normalizedRoleCode,
                projectId,
                (long) (page - 1) * size,
                size
        ).stream().map(this::summary).toList();
        return PageResponse.of(page, size, total, items);
    }

    @Override
    @Transactional(readOnly = true)
    public UserFilterOptions filterOptions() {
        return new UserFilterOptions(userAccountMapper.selectProjectOptions().stream()
                .map(this::projectOption)
                .toList());
    }

    @Override
    @Transactional(readOnly = true)
    public UserSummary detail(long id) {
        return summary(requireSummary(id));
    }

    @Override
    @Transactional
    public UserSummary create(UserCreateRequest request, long operatorId) {
        validateDepartment(request.departmentId());
        Set<Long> roleIds = normalizedIds(request.roleIds());
        validateRoleIds(roleIds);
        validatePassword(request.password());

        UserAccount user = new UserAccount();
        user.setDepartmentId(request.departmentId());
        user.setUsername(request.username().trim().toLowerCase(Locale.ROOT));
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName().trim());
        user.setEmail(normalizeNullable(request.email(), true));
        user.setPhone(normalizeNullable(request.phone(), false));
        user.setEnabled(request.enabled());
        try {
            userAccountMapper.insert(user);
            if (!roleIds.isEmpty()) {
                userRoleMapper.insertRoles(user.getId(), roleIds, operatorId);
            }
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "Username or email already exists");
        }
        return detail(user.getId());
    }

    @Override
    @Transactional
    public List<UserSummary> createBatch(BatchUserCreateRequest request, long operatorId) {
        validateDepartment(request.departmentId());
        validatePassword(request.password());
        Set<Long> roleIds = normalizedIds(request.roleIds());
        validateRoleIds(roleIds);
        return request.users().stream()
                .map(entry -> create(
                        new UserCreateRequest(
                                request.departmentId(),
                                entry.username(),
                                request.password(),
                                entry.displayName(),
                                entry.email(),
                                entry.phone(),
                                request.enabled(),
                                roleIds
                        ),
                        operatorId
                ))
                .toList();
    }

    @Override
    @Transactional
    public UserSummary update(long id, UserUpdateRequest request) {
        UserAccount user = requireUser(id);
        validateDepartment(request.departmentId());
        user.setDepartmentId(request.departmentId());
        user.setDisplayName(request.displayName().trim());
        user.setEmail(normalizeNullable(request.email(), true));
        user.setPhone(normalizeNullable(request.phone(), false));
        user.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        try {
            userAccountMapper.updateById(user);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "Email already exists");
        }
        return detail(id);
    }

    @Override
    @Transactional
    public void changeStatus(long id, boolean enabled, long operatorId) {
        UserAccount user = requireUser(id);
        if (id == operatorId && !enabled) {
            throw new BusinessException(ErrorCode.CONFLICT, "Current user cannot disable itself");
        }
        user.setEnabled(enabled);
        user.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        userAccountMapper.updateById(user);
        if (!enabled) {
            authSessionMapper.revokeAllByUserId(id, OffsetDateTime.now(ZoneOffset.UTC));
        }
    }

    @Override
    @Transactional
    public void resetPassword(long id, String newPassword) {
        UserAccount user = requireUser(id);
        validatePassword(newPassword);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(now);
        user.setUpdatedAt(now);
        userAccountMapper.updateById(user);
        authSessionMapper.revokeAllByUserId(id, now);
    }

    @Override
    @Transactional
    public void assignRoles(long id, Set<Long> roleIds, long operatorId) {
        requireUser(id);
        Set<Long> normalized = normalizedIds(roleIds);
        validateRoleIds(normalized);
        userRoleMapper.deleteByUserId(id);
        if (!normalized.isEmpty()) {
            userRoleMapper.insertRoles(id, normalized, operatorId);
        }
    }

    @Override
    @Transactional
    public void delete(long id, long operatorId) {
        if (id == operatorId) {
            throw new BusinessException(ErrorCode.CONFLICT, "Current user cannot delete itself");
        }
        requireUser(id);
        try {
            userAccountMapper.deleteById(id);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "User has related business data and cannot be deleted"
            );
        }
    }

    private UserSummaryRow requireSummary(long id) {
        UserSummaryRow row = userAccountMapper.selectSummaryById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "User not found");
        }
        return row;
    }

    private UserAccount requireUser(long id) {
        UserAccount user = userAccountMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "User not found");
        }
        return user;
    }

    private void validateDepartment(Long departmentId) {
        if (departmentId != null && departmentMapper.selectById(departmentId) == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Department does not exist");
        }
    }

    private void validateRoleIds(Set<Long> roleIds) {
        if (!roleIds.isEmpty() && roleMapper.countByIds(roleIds) != roleIds.size()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "One or more roles do not exist");
        }
    }

    private void validatePassword(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Password exceeds 72 UTF-8 bytes");
        }
    }

    private Set<Long> normalizedIds(Set<Long> ids) {
        return ids == null ? Set.of() : new LinkedHashSet<>(ids);
    }

    private UserSummary summary(UserSummaryRow row) {
        return new UserSummary(
                row.getId(),
                row.getDepartmentId(),
                row.getDepartmentName(),
                row.getUsername(),
                row.getDisplayName(),
                row.getEmail(),
                row.getPhone(),
                Boolean.TRUE.equals(row.getEnabled()),
                row.getLastLoginAt(),
                row.getCreatedAt(),
                parseRoles(row.getRolesJson())
        );
    }

    private List<RoleBrief> parseRoles(String rolesJson) {
        try {
            JsonNode roles = objectMapper.readTree(rolesJson);
            return objectMapper.readerForListOf(RoleBrief.class).readValue(roles);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not decode aggregated role data", exception);
        }
    }

    private UserProjectOption projectOption(OptionRow row) {
        return new UserProjectOption(row.id, row.name);
    }

    private String normalizeNullable(String value, boolean lowerCase) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return lowerCase ? normalized.toLowerCase(Locale.ROOT) : normalized;
    }

    private String normalizeEnum(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }
}

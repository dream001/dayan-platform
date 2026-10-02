package com.dayan.platform.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dayan.platform.common.api.ErrorCode;
import com.dayan.platform.common.exception.BusinessException;
import com.dayan.platform.dto.RbacDtos.MenuOrderRequest;
import com.dayan.platform.dto.RbacDtos.MenuRequest;
import com.dayan.platform.model.MenuPermission;
import com.dayan.platform.repository.mapper.MenuPermissionMapper;
import com.dayan.platform.repository.mapper.RolePermissionMapper;
import com.dayan.platform.service.MenuService;
import com.dayan.platform.vo.RbacViews.MenuNode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class MenuServiceImpl implements MenuService {

    private static final Pattern PERMISSION_CODE =
            Pattern.compile("^[a-z][a-z0-9-]*(?::[a-z][a-z0-9-]*)+$");

    private final MenuPermissionMapper menuPermissionMapper;
    private final RolePermissionMapper rolePermissionMapper;

    public MenuServiceImpl(
            MenuPermissionMapper menuPermissionMapper,
            RolePermissionMapper rolePermissionMapper
    ) {
        this.menuPermissionMapper = menuPermissionMapper;
        this.rolePermissionMapper = rolePermissionMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MenuNode> tree() {
        return buildTree(menuPermissionMapper.selectList(
                Wrappers.<MenuPermission>lambdaQuery()
                        .orderByAsc(MenuPermission::getSortOrder)
                        .orderByAsc(MenuPermission::getId)
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public MenuNode detail(long id) {
        requirePermission(id);
        return findNode(tree(), id);
    }

    @Override
    @Transactional
    public MenuNode create(MenuRequest request) {
        validateRequest(null, request);
        MenuPermission permission = new MenuPermission();
        apply(permission, request);
        try {
            menuPermissionMapper.insert(permission);
            if (rolePermissionMapper.grantToAdministrator(permission.getId()) != 1) {
                throw new IllegalStateException("Enabled administrator role is missing");
            }
        } catch (DataIntegrityViolationException exception) {
            throw conflict("Permission code already exists");
        }
        return node(permission, List.of());
    }

    @Override
    @Transactional
    public MenuNode update(long id, MenuRequest request) {
        MenuPermission permission = requirePermission(id);
        validateRequest(id, request);
        if ("BUTTON".equals(request.type()) && menuPermissionMapper.countChildren(id) > 0) {
            throw conflict("Permission with children cannot become a button");
        }
        apply(permission, request);
        permission.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        try {
            menuPermissionMapper.updateById(permission);
        } catch (DataIntegrityViolationException exception) {
            throw conflict("Permission code already exists");
        }
        return detail(id);
    }

    @Override
    @Transactional
    public List<MenuNode> reorder(MenuOrderRequest request) {
        List<MenuPermission> siblings = menuPermissionMapper.selectSiblingsForUpdate(request.parentId());
        List<Long> requestedIds = request.ids();
        Set<Long> uniqueIds = new HashSet<>(requestedIds);
        Set<Long> siblingIds = siblings.stream()
                .map(MenuPermission::getId)
                .collect(Collectors.toSet());
        if (uniqueIds.size() != requestedIds.size() || !uniqueIds.equals(siblingIds)) {
            throw new BusinessException(
                    ErrorCode.INVALID_ARGUMENT,
                    "ids must contain every sibling exactly once"
            );
        }

        Map<Long, MenuPermission> byId = siblings.stream()
                .collect(Collectors.toMap(MenuPermission::getId, item -> item));
        OffsetDateTime updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        for (int index = 0; index < requestedIds.size(); index++) {
            MenuPermission permission = byId.get(requestedIds.get(index));
            permission.setSortOrder(index * 10);
            permission.setUpdatedAt(updatedAt);
            menuPermissionMapper.updateById(permission);
        }
        return tree();
    }

    @Override
    @Transactional
    public void delete(long id) {
        requirePermission(id);
        if (menuPermissionMapper.countChildren(id) > 0) {
            throw conflict("Permission still has child nodes");
        }
        if (rolePermissionMapper.countByPermissionIdExcludingAdministrator(id) > 0) {
            throw conflict("Permission is still granted to roles");
        }
        menuPermissionMapper.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MenuNode> currentUserMenus(long userId) {
        return buildTree(menuPermissionMapper.selectAccessibleMenus(userId));
    }

    private void validateRequest(Long id, MenuRequest request) {
        if (request.sortOrder() < 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "sortOrder must not be negative");
        }
        String code = normalizeCode(request.code());
        if ("BUTTON".equals(request.type()) && code == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Button permission code is required");
        }
        if (code != null && !PERMISSION_CODE.matcher(code).matches()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Permission code format is invalid");
        }

        Long parentId = request.parentId();
        if ("BUTTON".equals(request.type()) && parentId == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Button must belong to a menu");
        }
        if (parentId == null) {
            return;
        }
        if (id != null && id.equals(parentId)) {
            throw conflict("Permission cannot be its own parent");
        }
        MenuPermission parent = requirePermission(parentId);
        if (!"MENU".equals(parent.getType())) {
            throw conflict("Parent permission must be a menu");
        }
        if (id != null && menuPermissionMapper.countDescendant(id, parentId) > 0) {
            throw conflict("Permission parent would create a cycle");
        }
    }

    private MenuPermission requirePermission(long id) {
        MenuPermission permission = menuPermissionMapper.selectById(id);
        if (permission == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Menu permission not found");
        }
        return permission;
    }

    private void apply(MenuPermission permission, MenuRequest request) {
        permission.setParentId(request.parentId());
        permission.setType(request.type().toUpperCase(Locale.ROOT));
        permission.setName(request.name().trim());
        permission.setCode(normalizeCode(request.code()));
        permission.setPath(normalizeNullable(request.path()));
        permission.setComponent(normalizeNullable(request.component()));
        permission.setIcon(normalizeNullable(request.icon()));
        permission.setSortOrder(request.sortOrder());
        permission.setVisible(request.visible());
        permission.setEnabled(request.enabled());
    }

    private List<MenuNode> buildTree(List<MenuPermission> permissions) {
        Map<Long, List<MenuPermission>> children = new LinkedHashMap<>();
        for (MenuPermission permission : permissions) {
            children.computeIfAbsent(permission.getParentId(), ignored -> new ArrayList<>()).add(permission);
        }
        Comparator<MenuPermission> order = Comparator.comparing(MenuPermission::getSortOrder)
                .thenComparing(MenuPermission::getId);
        children.values().forEach(items -> items.sort(order));
        return children.getOrDefault(null, List.of()).stream()
                .map(item -> buildNode(item, children))
                .toList();
    }

    private MenuNode buildNode(
            MenuPermission permission,
            Map<Long, List<MenuPermission>> children
    ) {
        List<MenuNode> childNodes = children.getOrDefault(permission.getId(), List.of()).stream()
                .map(item -> buildNode(item, children))
                .toList();
        return node(permission, childNodes);
    }

    private MenuNode findNode(List<MenuNode> nodes, long id) {
        for (MenuNode node : nodes) {
            if (node.id() == id) {
                return node;
            }
            MenuNode child = findNode(node.children(), id);
            if (child != null) {
                return child;
            }
        }
        return null;
    }

    private MenuNode node(MenuPermission permission, List<MenuNode> children) {
        return new MenuNode(
                permission.getId(),
                permission.getParentId(),
                permission.getType(),
                permission.getName(),
                permission.getCode(),
                permission.getPath(),
                permission.getComponent(),
                permission.getIcon(),
                permission.getSortOrder(),
                Boolean.TRUE.equals(permission.getVisible()),
                Boolean.TRUE.equals(permission.getEnabled()),
                children
        );
    }

    private String normalizeCode(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : null;
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private BusinessException conflict(String message) {
        return new BusinessException(ErrorCode.CONFLICT, message);
    }
}

package com.dayan.platform.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dayan.platform.common.api.ErrorCode;
import com.dayan.platform.common.exception.BusinessException;
import com.dayan.platform.dto.RbacDtos.DepartmentRequest;
import com.dayan.platform.model.Department;
import com.dayan.platform.repository.mapper.DepartmentMapper;
import com.dayan.platform.service.DepartmentService;
import com.dayan.platform.vo.RbacViews.DepartmentNode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentMapper departmentMapper;

    public DepartmentServiceImpl(DepartmentMapper departmentMapper) {
        this.departmentMapper = departmentMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DepartmentNode> tree() {
        List<Department> departments = departmentMapper.selectList(
                Wrappers.<Department>lambdaQuery()
                        .orderByAsc(Department::getSortOrder)
                        .orderByAsc(Department::getId)
        );
        return buildTree(departments);
    }

    @Override
    @Transactional
    public DepartmentNode create(DepartmentRequest request) {
        validateRequest(null, request);
        Department department = new Department();
        apply(department, request);
        try {
            departmentMapper.insert(department);
        } catch (DataIntegrityViolationException exception) {
            throw conflict("Department code already exists");
        }
        return node(department, List.of());
    }

    @Override
    @Transactional
    public DepartmentNode update(long id, DepartmentRequest request) {
        Department department = requireDepartment(id);
        validateRequest(id, request);
        apply(department, request);
        department.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        try {
            departmentMapper.updateById(department);
        } catch (DataIntegrityViolationException exception) {
            throw conflict("Department code already exists");
        }
        return node(department, List.of());
    }

    @Override
    @Transactional
    public void delete(long id) {
        requireDepartment(id);
        if (departmentMapper.countChildren(id) > 0) {
            throw conflict("Department still has child departments");
        }
        if (departmentMapper.countUsers(id) > 0) {
            throw conflict("Department still has users");
        }
        try {
            departmentMapper.deleteById(id);
        } catch (DataIntegrityViolationException exception) {
            throw conflict("Department is still referenced");
        }
    }

    private void validateRequest(Long id, DepartmentRequest request) {
        if (request.sortOrder() < 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "sortOrder must not be negative");
        }
        Long parentId = request.parentId();
        if (parentId == null) {
            return;
        }
        if (id != null && id.equals(parentId)) {
            throw conflict("Department cannot be its own parent");
        }
        requireDepartment(parentId);
        if (id != null && departmentMapper.countDescendant(id, parentId) > 0) {
            throw conflict("Department parent would create a cycle");
        }
    }

    private Department requireDepartment(long id) {
        Department department = departmentMapper.selectById(id);
        if (department == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Department not found");
        }
        return department;
    }

    private void apply(Department department, DepartmentRequest request) {
        department.setParentId(request.parentId());
        department.setName(request.name().trim());
        department.setCode(request.code().trim());
        department.setSortOrder(request.sortOrder());
        department.setEnabled(request.enabled());
    }

    private List<DepartmentNode> buildTree(List<Department> departments) {
        Map<Long, List<Department>> children = new LinkedHashMap<>();
        for (Department department : departments) {
            children.computeIfAbsent(department.getParentId(), ignored -> new ArrayList<>()).add(department);
        }
        Comparator<Department> order = Comparator.comparing(Department::getSortOrder)
                .thenComparing(Department::getId);
        children.values().forEach(items -> items.sort(order));
        return children.getOrDefault(null, List.of()).stream()
                .map(item -> buildNode(item, children))
                .toList();
    }

    private DepartmentNode buildNode(Department department, Map<Long, List<Department>> children) {
        List<DepartmentNode> childNodes = children.getOrDefault(department.getId(), List.of()).stream()
                .map(item -> buildNode(item, children))
                .toList();
        return node(department, childNodes);
    }

    private DepartmentNode node(Department department, List<DepartmentNode> children) {
        return new DepartmentNode(
                department.getId(),
                department.getParentId(),
                department.getName(),
                department.getCode(),
                department.getSortOrder(),
                Boolean.TRUE.equals(department.getEnabled()),
                children
        );
    }

    private BusinessException conflict(String message) {
        return new BusinessException(ErrorCode.CONFLICT, message);
    }
}

package com.dayan.platform.controller;

import com.dayan.platform.audit.Audited;
import com.dayan.platform.dto.RbacDtos.DepartmentRequest;
import com.dayan.platform.service.DepartmentService;
import com.dayan.platform.vo.RbacViews.DepartmentNode;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${app.api.base-path}/system/departments")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('system:department:view')")
    public List<DepartmentNode> tree() {
        return departmentService.tree();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:department:create')")
    @Audited(
            module = "DEPARTMENT",
            action = "CREATE",
            targetType = "DEPARTMENT",
            targetId = "#result == null ? #request.code() : #result.id()"
    )
    public DepartmentNode create(@Valid @RequestBody DepartmentRequest request) {
        return departmentService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:department:update')")
    @Audited(module = "DEPARTMENT", action = "UPDATE", targetType = "DEPARTMENT", targetId = "#id")
    public DepartmentNode update(
            @PathVariable long id,
            @Valid @RequestBody DepartmentRequest request
    ) {
        return departmentService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:department:delete')")
    @Audited(module = "DEPARTMENT", action = "DELETE", targetType = "DEPARTMENT", targetId = "#id")
    public void delete(@PathVariable long id) {
        departmentService.delete(id);
    }
}

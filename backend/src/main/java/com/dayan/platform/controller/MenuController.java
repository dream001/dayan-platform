package com.dayan.platform.controller;

import com.dayan.platform.audit.Audited;
import com.dayan.platform.dto.RbacDtos.MenuRequest;
import com.dayan.platform.service.MenuService;
import com.dayan.platform.vo.RbacViews.MenuNode;
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
@RequestMapping("${app.api.base-path}/system/menus")
public class MenuController {

    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('system:permission:view')")
    public List<MenuNode> tree() {
        return menuService.tree();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system:permission:view')")
    public MenuNode detail(@PathVariable long id) {
        return menuService.detail(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system:permission:create')")
    @Audited(
            module = "MENU",
            action = "CREATE",
            targetType = "MENU_PERMISSION",
            targetId = "#result == null ? #request.code() : #result.id()"
    )
    public MenuNode create(@Valid @RequestBody MenuRequest request) {
        return menuService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('system:permission:update')")
    @Audited(module = "MENU", action = "UPDATE", targetType = "MENU_PERMISSION", targetId = "#id")
    public MenuNode update(
            @PathVariable long id,
            @Valid @RequestBody MenuRequest request
    ) {
        return menuService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('system:permission:delete')")
    @Audited(module = "MENU", action = "DELETE", targetType = "MENU_PERMISSION", targetId = "#id")
    public void delete(@PathVariable long id) {
        menuService.delete(id);
    }
}

package com.dayan.platform.controller;

import com.dayan.platform.service.MenuService;
import com.dayan.platform.vo.RbacViews.MenuNode;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${app.api.base-path}/auth/me")
public class CurrentMenuController {

    private final MenuService menuService;

    public CurrentMenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping("/menus")
    @PreAuthorize("isAuthenticated()")
    public List<MenuNode> menus(@AuthenticationPrincipal Jwt jwt) {
        long userId = ((Number) jwt.getClaim("uid")).longValue();
        return menuService.currentUserMenus(userId);
    }
}

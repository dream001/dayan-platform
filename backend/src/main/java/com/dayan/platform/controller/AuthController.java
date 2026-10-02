package com.dayan.platform.controller;

import com.dayan.platform.audit.Audited;
import com.dayan.platform.dto.AuthDtos.LoginRequest;
import com.dayan.platform.dto.AuthDtos.LogoutRequest;
import com.dayan.platform.dto.AuthDtos.PasswordChangeRequest;
import com.dayan.platform.dto.AuthDtos.ProfileUpdateRequest;
import com.dayan.platform.dto.AuthDtos.RefreshRequest;
import com.dayan.platform.service.AuthService;
import com.dayan.platform.service.AuthService.ClientMetadata;
import com.dayan.platform.vo.AuthViews.TokenView;
import com.dayan.platform.vo.AuthViews.UserView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${app.api.base-path}/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Audited(
            module = "AUTH",
            action = "LOGIN",
            targetType = "USER",
            targetId = "#request.username()",
            operatorId = "#result == null ? null : #result.user().id()",
            operatorName = "#result == null ? #request.username() : #result.user().username()"
    )
    public TokenView login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest
    ) {
        return authService.login(request, clientMetadata(servletRequest));
    }

    @PostMapping("/refresh")
    @Audited(
            module = "AUTH",
            action = "REFRESH_TOKEN",
            targetType = "SESSION",
            targetId = "#result == null ? null : #result.user().id()",
            operatorId = "#result == null ? null : #result.user().id()",
            operatorName = "#result == null ? null : #result.user().username()"
    )
    public TokenView refresh(
            @Valid @RequestBody RefreshRequest request,
            HttpServletRequest servletRequest
    ) {
        return authService.refresh(request.refreshToken(), clientMetadata(servletRequest));
    }

    @PostMapping("/logout")
    @Audited(module = "AUTH", action = "LOGOUT", targetType = "SESSION")
    public void logout(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody LogoutRequest request
    ) {
        authService.logout(userId(jwt), request.refreshToken());
    }

    @GetMapping("/me")
    public UserView currentUser(@AuthenticationPrincipal Jwt jwt) {
        return authService.currentUser(userId(jwt));
    }

    @PatchMapping("/me/profile")
    @Audited(module = "AUTH", action = "UPDATE_PROFILE", targetType = "USER")
    public UserView updateProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ProfileUpdateRequest request
    ) {
        return authService.updateProfile(userId(jwt), request);
    }

    @PutMapping("/me/password")
    @Audited(module = "AUTH", action = "CHANGE_PASSWORD", targetType = "USER")
    public void changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PasswordChangeRequest request
    ) {
        authService.changePassword(userId(jwt), sessionId(jwt), request);
    }

    private long userId(Jwt jwt) {
        return ((Number) jwt.getClaim("uid")).longValue();
    }

    private long sessionId(Jwt jwt) {
        return ((Number) jwt.getClaim("sid")).longValue();
    }

    private ClientMetadata clientMetadata(HttpServletRequest request) {
        return new ClientMetadata(request.getRemoteAddr(), request.getHeader("User-Agent"));
    }
}

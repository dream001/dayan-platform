package com.dayan.platform.vo;

import java.util.List;

public final class AuthViews {

    private AuthViews() {
    }

    public record UserView(
            long id,
            String username,
            String displayName,
            String email,
            String phone,
            Long departmentId,
            List<String> permissions
    ) {
        public UserView {
            permissions = List.copyOf(permissions);
        }
    }

    public record TokenView(
            String tokenType,
            String accessToken,
            long expiresIn,
            String refreshToken,
            UserView user
    ) {
    }
}

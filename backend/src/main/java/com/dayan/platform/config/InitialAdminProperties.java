package com.dayan.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.initial-admin")
public record InitialAdminProperties(
        boolean enabled,
        String username,
        String displayName,
        String email,
        String password
) {
}

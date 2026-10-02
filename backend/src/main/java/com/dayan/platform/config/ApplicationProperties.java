package com.dayan.platform.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app")
public record ApplicationProperties(
        @NotBlank String environment,
        @NotBlank String version,
        @Valid Api api,
        @Valid Security security
) {

    public record Api(
            @NotBlank
            @Pattern(regexp = "/.*", message = "must start with '/'")
            String basePath,
            @NotBlank String requestIdHeader
    ) {
    }

    public record Security(
            @NotBlank String jwtSecret,
            @NotBlank String issuer,
            @NotNull Duration accessTokenTtl,
            @NotNull Duration refreshTokenTtl,
            @NotEmpty List<@NotBlank String> allowedOrigins
    ) {
        public Security {
            if (accessTokenTtl != null && (accessTokenTtl.isZero() || accessTokenTtl.isNegative())) {
                throw new IllegalArgumentException("accessTokenTtl must be positive");
            }
            if (refreshTokenTtl != null && (refreshTokenTtl.isZero() || refreshTokenTtl.isNegative())) {
                throw new IllegalArgumentException("refreshTokenTtl must be positive");
            }
            allowedOrigins = List.copyOf(allowedOrigins);
        }
    }
}

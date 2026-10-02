package com.dayan.platform.config;

import com.dayan.platform.common.api.ApiResponse;
import com.dayan.platform.common.api.ErrorCode;
import com.dayan.platform.security.PlatformUserDetailsService;
import com.dayan.platform.security.PlatformUserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

    @Bean
    public AuthenticationManager authenticationManager(
            PlatformUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    public JwtEncoder jwtEncoder(ApplicationProperties properties) {
        SecretKey secretKey = jwtSecret(properties);
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(ApplicationProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecret(properties))
                .macAlgorithm(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.security().issuer()));
        return decoder;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ApplicationProperties properties,
            PlatformUserDetailsService userDetailsService,
            ObjectMapper objectMapper
    ) throws Exception {
        String api = properties.api().basePath();
        AuthenticationEntryPoint unauthorized = (request, response, exception) ->
                writeError(response, objectMapper, ErrorCode.UNAUTHORIZED);
        AccessDeniedHandler forbidden = (request, response, exception) ->
                writeError(response, objectMapper, ErrorCode.FORBIDDEN);

        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource(properties)))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, api + "/auth/login", api + "/auth/refresh").permitAll()
                        .requestMatchers(
                                api + "/system/info",
                                api + "/missing",
                                "/actuator/health",
                                "/actuator/health/**",
                                "/test/**"
                        )
                        .permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(token ->
                                authenticatedToken(token, userDetailsService))));
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(ApplicationProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.security().allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id"));
        configuration.setExposedHeaders(List.of(properties.api().requestIdHeader()));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private AbstractAuthenticationToken authenticatedToken(
            Jwt jwt,
            PlatformUserDetailsService userDetailsService
    ) {
        Number userId = jwt.getClaim("uid");
        if (userId == null) {
            throw new DisabledException("Invalid token subject");
        }
        PlatformUserPrincipal principal = userDetailsService.loadById(userId.longValue());
        if (!principal.isEnabled() || !principal.getUsername().equals(jwt.getSubject())) {
            throw new DisabledException("User is disabled");
        }
        return new JwtAuthenticationToken(jwt, principal.getAuthorities(), principal.getUsername());
    }

    private SecretKey jwtSecret(ApplicationProperties properties) {
        byte[] secret = properties.security().jwtSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("JWT_SECRET must contain at least 32 UTF-8 bytes");
        }
        return new SecretKeySpec(secret, "HmacSHA256");
    }

    private void writeError(
            jakarta.servlet.http.HttpServletResponse response,
            ObjectMapper objectMapper,
            ErrorCode errorCode
    ) throws IOException {
        response.setStatus(errorCode.httpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(),
                ApiResponse.error(errorCode, errorCode.defaultMessage())
        );
    }
}

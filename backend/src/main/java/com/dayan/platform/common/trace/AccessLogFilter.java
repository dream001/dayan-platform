package com.dayan.platform.common.trace;

import com.dayan.platform.service.impl.AccessLogPersistenceService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(0)
public class AccessLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AccessLogFilter.class);

    private final AccessLogPersistenceService persistenceService;

    public AccessLogFilter(AccessLogPersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator/")
                || "OPTIONS".equals(request.getMethod());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long startedAt = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            persistSafely(request, response, startedAt);
        }
    }

    private void persistSafely(
            HttpServletRequest request,
            HttpServletResponse response,
            long startedAt
    ) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long userId = null;
        String username = null;
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            Number claim = jwtAuthentication.getToken().getClaim("uid");
            userId = claim == null ? null : claim.longValue();
            username = authentication.getName();
        }
        try {
            persistenceService.persist(
                    userId,
                    truncate(username, 64),
                    truncate(request.getMethod(), 10),
                    truncate(request.getRequestURI(), 1024),
                    response.getStatus(),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt),
                    truncate(clientAddress(request), 45),
                    truncate(request.getHeader("User-Agent"), 512),
                    truncate(requestId(request), 128),
                    OffsetDateTime.now(ZoneOffset.UTC)
            );
        } catch (RuntimeException exception) {
            log.warn("Unable to persist access log for {}", request.getRequestURI(), exception);
        }
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(RequestTrace.ATTRIBUTE);
        return value == null ? RequestTrace.currentRequestId() : value.toString();
    }

    private String clientAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",", 2)[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String truncate(String value, int length) {
        return value == null || value.length() <= length ? value : value.substring(0, length);
    }
}

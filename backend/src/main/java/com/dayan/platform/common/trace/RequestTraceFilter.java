package com.dayan.platform.common.trace;

import com.dayan.platform.config.ApplicationProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTraceFilter extends OncePerRequestFilter {

    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    private final ApplicationProperties properties;

    public RequestTraceFilter(ApplicationProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String headerName = properties.api().requestIdHeader();
        String requestId = normalizeRequestId(request.getHeader(headerName));

        request.setAttribute(RequestTrace.ATTRIBUTE, requestId);
        response.setHeader(headerName, requestId);
        try (MDC.MDCCloseable ignored = MDC.putCloseable(RequestTrace.MDC_KEY, requestId)) {
            filterChain.doFilter(request, response);
        }
    }

    private String normalizeRequestId(String candidate) {
        if (candidate != null && SAFE_REQUEST_ID.matcher(candidate).matches()) {
            return candidate;
        }
        return UUID.randomUUID().toString();
    }
}

package com.dayan.platform.audit;

import com.dayan.platform.common.exception.BusinessException;
import com.dayan.platform.common.trace.RequestTrace;
import com.dayan.platform.model.OperationLog;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);
    private static final ExpressionParser EXPRESSIONS = new SpelExpressionParser();
    private static final DefaultParameterNameDiscoverer PARAMETER_NAMES =
            new DefaultParameterNameDiscoverer();

    private final AuditPersistenceService persistenceService;

    public AuditAspect(AuditPersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    @Around("@annotation(audited)")
    public Object record(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        long startedAt = System.nanoTime();
        Object result = null;
        Throwable failure = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable throwable) {
            failure = throwable;
            throw throwable;
        } finally {
            persistSafely(createLog(joinPoint, audited, result, failure, startedAt));
        }
    }

    private OperationLog createLog(
            ProceedingJoinPoint joinPoint,
            Audited audited,
            Object result,
            Throwable failure,
            long startedAt
    ) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long operatorId = authenticatedUserId(authentication);
        String operatorName = authenticatedUserName(authentication);
        EvaluationValues evaluated = evaluate(joinPoint, audited, result);
        if (evaluated.operatorId() != null) {
            operatorId = parseLong(evaluated.operatorId());
        }
        if (StringUtils.hasText(evaluated.operatorName())) {
            operatorName = evaluated.operatorName();
        }

        HttpServletRequest request = currentRequest();
        OperationLog operationLog = new OperationLog();
        operationLog.setOperatorId(operatorId);
        operationLog.setOperatorName(truncate(operatorName, 64));
        operationLog.setModule(audited.module());
        operationLog.setAction(audited.action());
        operationLog.setTargetType(audited.targetType());
        operationLog.setTargetId(truncate(evaluated.targetId(), 128));
        operationLog.setResult(failure == null ? "SUCCESS" : "FAILURE");
        operationLog.setErrorSummary(failure == null ? null : errorSummary(failure));
        operationLog.setIpAddress(request == null ? null : truncate(request.getRemoteAddr(), 45));
        operationLog.setUserAgent(request == null ? null : truncate(request.getHeader("User-Agent"), 512));
        operationLog.setRequestId(truncate(requestId(request), 128));
        operationLog.setDetails(requestDetails(request));
        operationLog.setOccurredAt(OffsetDateTime.now(ZoneOffset.UTC));
        operationLog.setDurationMs(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
        return operationLog;
    }

    private EvaluationValues evaluate(
            ProceedingJoinPoint joinPoint,
            Audited audited,
            Object result
    ) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
                null,
                signature.getMethod(),
                joinPoint.getArgs(),
                PARAMETER_NAMES
        );
        context.setVariable("result", result);
        return new EvaluationValues(
                evaluateText(audited.targetId(), context),
                evaluateText(audited.operatorId(), context),
                evaluateText(audited.operatorName(), context)
        );
    }

    private String evaluateText(String expression, MethodBasedEvaluationContext context) {
        if (!StringUtils.hasText(expression)) {
            return null;
        }
        try {
            Object value = EXPRESSIONS.parseExpression(expression).getValue(context);
            return value == null ? null : value.toString();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Long authenticatedUserId(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            Number userId = jwtAuthentication.getToken().getClaim("uid");
            return userId == null ? null : userId.longValue();
        }
        return null;
    }

    private String authenticatedUserName(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getName())
                ? authentication.getName()
                : null;
    }

    private String requestId(HttpServletRequest request) {
        if (request != null) {
            Object value = request.getAttribute(RequestTrace.ATTRIBUTE);
            if (value != null) {
                return value.toString();
            }
        }
        return RequestTrace.currentRequestId();
    }

    private String requestDetails(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        return "{\"method\":\"%s\",\"path\":\"%s\"}".formatted(
                jsonEscape(request.getMethod()),
                jsonEscape(request.getRequestURI())
        );
    }

    private String errorSummary(Throwable failure) {
        if (failure instanceof BusinessException businessException) {
            return truncate(
                    businessException.getErrorCode().code() + ": " + safeMessage(businessException.getMessage()),
                    500
            );
        }
        return truncate("Unexpected " + failure.getClass().getSimpleName(), 500);
    }

    private String safeMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return "Request failed";
        }
        return message.replaceAll(
                "(?i)(password|token|secret|authorization)\\s*[:=]\\s*[^,;\\s]+",
                "$1=[REDACTED]"
        );
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private Long parseLong(String value) {
        try {
            return value == null ? null : Long.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String truncate(String value, int maximumLength) {
        return value == null || value.length() <= maximumLength
                ? value
                : value.substring(0, maximumLength);
    }

    private void persistSafely(OperationLog operationLog) {
        try {
            persistenceService.persist(operationLog);
        } catch (RuntimeException persistenceFailure) {
            log.error(
                    "Could not persist audit record: module={}, action={}, requestId={}",
                    operationLog.getModule(),
                    operationLog.getAction(),
                    operationLog.getRequestId(),
                    persistenceFailure
            );
        }
    }

    private record EvaluationValues(String targetId, String operatorId, String operatorName) {
    }
}

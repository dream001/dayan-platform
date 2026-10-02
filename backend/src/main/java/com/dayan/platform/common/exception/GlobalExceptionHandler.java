package com.dayan.platform.common.exception;

import com.dayan.platform.common.api.ApiResponse;
import com.dayan.platform.common.api.ErrorCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        log.warn("Business request rejected: code={}, message={}", errorCode.code(), exception.getMessage());
        return ResponseEntity.status(errorCode.httpStatus())
                .body(ApiResponse.error(errorCode, exception.getMessage()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(
            AuthenticationException exception
    ) {
        log.debug("Authentication rejected", exception);
        return response(ErrorCode.UNAUTHORIZED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(
            AccessDeniedException exception
    ) {
        log.debug("Authorization rejected", exception);
        return response(ErrorCode.FORBIDDEN);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Map<String, List<String>>>> handleBindException(
            BindException exception
    ) {
        Map<String, List<String>> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.groupingBy(
                        FieldError::getField,
                        LinkedHashMap::new,
                        Collectors.mapping(DefaultMessageSourceResolvable::getDefaultMessage, Collectors.toList())
                ));
        return invalidArgument("Validation failed", fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Map<String, List<String>>>> handleConstraintViolation(
            ConstraintViolationException exception
    ) {
        Map<String, List<String>> fieldErrors = exception.getConstraintViolations().stream()
                .collect(Collectors.groupingBy(
                        violation -> leafProperty(violation),
                        LinkedHashMap::new,
                        Collectors.mapping(ConstraintViolation::getMessage, Collectors.toList())
                ));
        return invalidArgument("Validation failed", fieldErrors);
    }

    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleRequestParameterException(Exception exception) {
        log.debug("Invalid request parameter", exception);
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCode.INVALID_ARGUMENT, "Invalid request parameter"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException exception) {
        log.debug("Malformed request body", exception);
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCode.MALFORMED_REQUEST, ErrorCode.MALFORMED_REQUEST.defaultMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoResourceFoundException exception) {
        log.debug("Resource not found: {}", exception.getResourcePath());
        return response(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException exception
    ) {
        log.debug("HTTP method not allowed: {}", exception.getMethod());
        return response(ErrorCode.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException exception
    ) {
        log.debug("Unsupported media type: {}", exception.getContentType());
        return response(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(
            MaxUploadSizeExceededException exception
    ) {
        log.debug("Uploaded request exceeds configured size", exception);
        return response(ErrorCode.PAYLOAD_TOO_LARGE);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
        log.error("Unhandled request failure", exception);
        return ResponseEntity.internalServerError()
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage()));
    }

    private ResponseEntity<ApiResponse<Map<String, List<String>>>> invalidArgument(
            String message,
            Map<String, List<String>> fieldErrors
    ) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCode.INVALID_ARGUMENT, message, fieldErrors));
    }

    private ResponseEntity<ApiResponse<Void>> response(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.httpStatus())
                .body(ApiResponse.error(errorCode, errorCode.defaultMessage()));
    }

    private String leafProperty(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        int separator = path.lastIndexOf('.');
        return separator >= 0 ? path.substring(separator + 1) : path;
    }
}

package com.dayan.platform.common.api;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    SUCCESS("OK", "Success", HttpStatus.OK),
    INVALID_ARGUMENT("COMMON_INVALID_ARGUMENT", "Invalid request parameters", HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST("COMMON_MALFORMED_REQUEST", "Malformed request body", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED("AUTH_UNAUTHORIZED", "Authentication required", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("AUTH_FORBIDDEN", "Access denied", HttpStatus.FORBIDDEN),
    INVALID_CREDENTIALS("AUTH_INVALID_CREDENTIALS", "Invalid username or password", HttpStatus.UNAUTHORIZED),
    INVALID_REFRESH_TOKEN("AUTH_INVALID_REFRESH_TOKEN", "Invalid or expired refresh token", HttpStatus.UNAUTHORIZED),
    CURRENT_PASSWORD_INVALID("AUTH_CURRENT_PASSWORD_INVALID", "Current password is incorrect", HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND("COMMON_RESOURCE_NOT_FOUND", "Resource not found", HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED("COMMON_METHOD_NOT_ALLOWED", "HTTP method not allowed", HttpStatus.METHOD_NOT_ALLOWED),
    UNSUPPORTED_MEDIA_TYPE(
            "COMMON_UNSUPPORTED_MEDIA_TYPE",
            "Unsupported media type",
            HttpStatus.UNSUPPORTED_MEDIA_TYPE
    ),
    PAYLOAD_TOO_LARGE("FILE_TOO_LARGE", "Uploaded file is too large", HttpStatus.PAYLOAD_TOO_LARGE),
    FILE_TYPE_NOT_ALLOWED(
            "FILE_TYPE_NOT_ALLOWED",
            "Uploaded file type is not allowed",
            HttpStatus.UNSUPPORTED_MEDIA_TYPE
    ),
    FILE_STORAGE_ERROR(
            "FILE_STORAGE_ERROR",
            "File storage operation failed",
            HttpStatus.SERVICE_UNAVAILABLE
    ),
    CONFLICT("COMMON_CONFLICT", "Resource conflict", HttpStatus.CONFLICT),
    INTERNAL_ERROR("COMMON_INTERNAL_ERROR", "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;

    ErrorCode(String code, String defaultMessage, HttpStatus httpStatus) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
    }

    public String code() {
        return code;
    }

    public String defaultMessage() {
        return defaultMessage;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }
}

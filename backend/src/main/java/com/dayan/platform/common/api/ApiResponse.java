package com.dayan.platform.common.api;

import com.dayan.platform.common.trace.RequestTrace;
import java.time.Instant;

public record ApiResponse<T>(
        String code,
        String message,
        T data,
        String requestId,
        Instant timestamp
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(
                ErrorCode.SUCCESS.code(),
                ErrorCode.SUCCESS.defaultMessage(),
                data,
                RequestTrace.currentRequestId(),
                Instant.now()
        );
    }

    public static ApiResponse<Void> error(ErrorCode errorCode, String message) {
        return error(errorCode, message, null);
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message, T data) {
        return new ApiResponse<>(
                errorCode.code(),
                message,
                data,
                RequestTrace.currentRequestId(),
                Instant.now()
        );
    }
}

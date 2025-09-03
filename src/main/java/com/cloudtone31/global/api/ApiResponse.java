package com.cloudtone31.global.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 모든 API의 응답을 이 포맷으로 감쌉니다.
 *   { "success": true/false, "data": ..., "message": "..." }
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        String message
) {
    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, data, message);
    }

    public static ApiResponse<?> ok(String message) {
        return new ApiResponse<>(true, null, message);
    }

    public static ApiResponse<?> fail(String message) {
        return new ApiResponse<>(false, null, message);
    }
}
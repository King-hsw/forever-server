package com.forever.server.common;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 统一响应体：{"code": 0, "message": "ok", "data": {...}}
 */
@Schema(description = "统一响应体")
public record ApiResponse<T>(
        @Schema(description = "业务码，0 表示成功，非 0 见错误码定义", example = "0") int code,
        @Schema(description = "提示信息", example = "ok") String message,
        @Schema(description = "业务数据") T data) {

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(ErrorCode.SUCCESS.code(), ErrorCode.SUCCESS.message(), null);
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(ErrorCode.SUCCESS.code(), ErrorCode.SUCCESS.message(), data);
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        return new ApiResponse<>(errorCode.code(), errorCode.message(), null);
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message) {
        return new ApiResponse<>(errorCode.code(), message, null);
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, T data) {
        return new ApiResponse<>(errorCode.code(), errorCode.message(), data);
    }
}

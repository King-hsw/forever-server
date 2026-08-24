package com.forever.server.common;

import org.springframework.http.HttpStatus;

/**
 * 业务错误码与 HTTP 状态码对照（见设计文档第 10 节）。
 */
public enum ErrorCode {

    SUCCESS(0, HttpStatus.OK, "ok"),
    BAD_REQUEST(40001, HttpStatus.BAD_REQUEST, "参数校验失败"),
    UNAUTHORIZED(40101, HttpStatus.UNAUTHORIZED, "未登录或凭证无效"),
    FORBIDDEN(40301, HttpStatus.FORBIDDEN, "无权限"),
    NOT_FOUND(40401, HttpStatus.NOT_FOUND, "资源不存在"),
    CONFLICT(40901, HttpStatus.CONFLICT, "业务冲突"),
    INTERNAL_ERROR(50000, HttpStatus.INTERNAL_SERVER_ERROR, "系统内部错误");

    private final int code;
    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(int code, HttpStatus httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public int code() {
        return code;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String message() {
        return message;
    }
}

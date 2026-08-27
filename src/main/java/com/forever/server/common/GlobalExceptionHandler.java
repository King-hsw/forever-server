package com.forever.server.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Object>> handleBizException(BizException e) {
        // 业务异常是预期内的，warn 一行即可，不打堆栈
        log.warn("biz exception: code={}, message={}", e.errorCode().code(), e.getMessage());
        return ResponseEntity.status(e.errorCode().httpStatus())
                .body(new ApiResponse<>(e.errorCode().code(), e.getMessage(), e.data()));
    }

    /** Bean Validation 校验失败：data 返回字段级错误明细 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> fieldErrors = new HashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(fe -> fieldErrors.put(fe.getField(), fe.getDefaultMessage()));
        log.warn("request validation failed: {}", fieldErrors);
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCode.BAD_REQUEST, fieldErrors));
    }

    /** 权限校验拒绝（PermInterceptor / Spring Security）：统一 403，不当作服务端错误 */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthorizationDenied(AccessDeniedException e) {
        log.warn("access denied: {}", e.getMessage());
        return ResponseEntity.status(ErrorCode.FORBIDDEN.httpStatus())
                .body(ApiResponse.error(ErrorCode.FORBIDDEN));
    }

    /** 兜底：记录详情日志，响应不泄露堆栈 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception e) {
        log.error("unhandled exception", e);
        return ResponseEntity.internalServerError().body(ApiResponse.error(ErrorCode.INTERNAL_ERROR));
    }
}

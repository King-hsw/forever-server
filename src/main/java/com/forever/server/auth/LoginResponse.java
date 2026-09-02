package com.forever.server.auth;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 登录响应体（双令牌：access 随请求提交，refresh 仅用于换新）
 */
@Schema(description = "登录响应")
public record LoginResponse(
        @Schema(description = "访问令牌，放在 Authorization: Bearer 头中，短期有效") String accessToken,
        @Schema(description = "刷新令牌，仅用于 POST /api/auth/refresh 换新令牌对") String refreshToken,
        @Schema(description = "访问令牌有效期（秒）", example = "7200") long expiresIn,
        @Schema(description = "刷新令牌有效期（秒）", example = "2592000") long refreshExpiresIn) {
}

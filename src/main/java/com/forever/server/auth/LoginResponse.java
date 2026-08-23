package com.forever.server.auth;

import io.swagger.v3.oas.annotations.media.Schema;

/** 登录响应体 */
@Schema(description = "登录响应")
public record LoginResponse(
        @Schema(description = "JWT 访问令牌，后续请求放在 Authorization: Bearer 头中") String accessToken,
        @Schema(description = "令牌有效期（秒）", example = "86400") long expiresIn) {
}

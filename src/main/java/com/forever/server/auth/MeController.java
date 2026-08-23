package com.forever.server.auth;

import com.forever.server.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前登录管理员信息（从 JWT claims 读取，无需查库）。
 */
@Tag(name = "管理员信息", description = "当前登录用户信息，从 JWT claims 读取，无需查库")
@RestController
@RequestMapping("/api/admin")
public class MeController {

    public record MeResponse(long uid, String username) {
    }

    @Operation(summary = "获取当前登录用户信息", description = "从 JWT 解析 uid 和用户名，不查库")
    @GetMapping("/me")
    public ApiResponse<MeResponse> me(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        long uid = jwt.getClaim("uid");
        return ApiResponse.ok(new MeResponse(uid, jwt.getSubject()));
    }
}

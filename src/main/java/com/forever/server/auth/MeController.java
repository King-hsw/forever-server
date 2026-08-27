package com.forever.server.auth;

import com.forever.server.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前登录用户信息：身份来自登录态令牌。
 */
@Tag(name = "登录用户信息", description = "当前登录用户的身份")
@RestController
@RequestMapping("/api/admin")
public class MeController {

    public record MeResponse(long uid, String username) {
    }

    @Operation(summary = "获取当前登录用户信息", description = "uid/用户名来自登录令牌")
    @GetMapping("/me")
    public ApiResponse<MeResponse> me(Authentication authentication) {
        AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
        return ApiResponse.ok(new MeResponse(principal.uid(), principal.username()));
    }
}

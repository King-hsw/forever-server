package com.forever.server.auth;

import com.forever.server.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "认证", description = "登录认证，登录成功后用返回的 JWT 访问 /api/admin/** 接口")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final TokenService tokenService;

    @Operation(summary = "登录", description = "校验用户名密码，签发双令牌（access + refresh）；登录成败均入审计日志")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                            HttpServletRequest httpRequest) {
        return ApiResponse.ok(authService.login(request, clientIp(httpRequest)));
    }

    @Operation(summary = "换发令牌", description = "用 refreshToken 换新令牌对；旧令牌对作废（轮换防重放）")
    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(@RequestBody RefreshRequest request) {
        return ApiResponse.ok(tokenService.rotate(request.refreshToken()));
    }

    @Operation(summary = "登出", description = "吊销该会话的令牌对；幂等，重复调用无副作用")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestBody RefreshRequest request) {
        tokenService.revokeByRefreshToken(request.refreshToken());
        return ApiResponse.ok();
    }

    public record RefreshRequest(String refreshToken) {
    }

    /**
     * 优先取代理转发头，兼容 Nginx 反代部署
     */
    private static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

package com.forever.server.auth;

import com.forever.server.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 当前登录用户信息：身份来自登录态令牌；角色与权限实时查 RBAC 缓存，
 * 后台调配权限后刷新页面即生效。
 */
@Perm // 显式声明：仅需登录态
@Tag(name = "登录用户信息", description = "当前登录用户的身份、角色与权限")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class MeController {

    public record MeResponse(long uid, String username, List<String> roles, List<String> permissions) {
    }

    private final RbacService rbacService;

    @Operation(summary = "获取当前登录用户信息", description = "uid/用户名来自登录令牌；roles/permissions 实时查库（带缓存）")
    @GetMapping("/me")
    public ApiResponse<MeResponse> me(Authentication authentication) {
        AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
        long uid = principal.uid();
        return ApiResponse.ok(new MeResponse(uid, principal.username(),
                rbacService.rolesOf(uid).stream().map(SysRole::getCode).toList(),
                List.copyOf(rbacService.permissionsOf(uid))));
    }
}

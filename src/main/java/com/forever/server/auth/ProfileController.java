package com.forever.server.auth;

import com.forever.server.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 当前登录用户自己的资料、头像与密码；仅需登录，不受 RBAC 权限码约束。
 */
@Tag(name = "个人资料", description = "当前登录用户的昵称 / 邮箱 / 主页 / 头像 / 密码")
@RestController
@RequestMapping("/api/admin/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    public record ProfileUpdateRequest(
            @Size(max = 50, message = "昵称最长 50 字") String nickname,
            String email,
            String site) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "原密码不能为空") String oldPassword,
            @NotBlank(message = "新密码不能为空") @Size(min = 6, max = 100, message = "新密码 6-100 位") String newPassword) {
    }

    @Operation(summary = "获取当前登录用户资料", description = "avatarUrl 已解析：自定义头像优先，其次邮箱 Gravatar")
    @GetMapping
    public ApiResponse<ProfileResponse> me(Authentication authentication) {
        AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
        return ApiResponse.ok(profileService.profileOf(principal.uid()));
    }

    @Operation(summary = "更新昵称 / 邮箱 / 个人主页", description = "留空表示清空对应字段")
    @PutMapping
    public ApiResponse<ProfileResponse> update(Authentication authentication,
                                               @Valid @RequestBody ProfileUpdateRequest request) {
        AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
        long uid = principal.uid();
        return ApiResponse.ok(profileService.update(uid, request.nickname(), request.email(), request.site()));
    }

    @Operation(summary = "上传头像", description = "jpg / png / webp，≤2MB；multipart 字段名 file")
    @PostMapping("/avatar")
    public ApiResponse<ProfileResponse> uploadAvatar(Authentication authentication,
                                                     @RequestParam("file") MultipartFile file) {
        AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
        return ApiResponse.ok(profileService.uploadAvatar(principal.uid(), file));
    }

    @Operation(summary = "删除自定义头像", description = "删除后头像回落为邮箱 Gravatar")
    @DeleteMapping("/avatar")
    public ApiResponse<ProfileResponse> removeAvatar(Authentication authentication) {
        AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
        return ApiResponse.ok(profileService.removeAvatar(principal.uid()));
    }

    @Operation(summary = "修改密码", description = "需校验原密码")
    @PutMapping("/password")
    public ApiResponse<Map<String, Object>> changePassword(Authentication authentication,
                                                           @Valid @RequestBody ChangePasswordRequest request) {
        AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
        profileService.changePassword(principal.uid(), request.oldPassword(), request.newPassword());
        return ApiResponse.ok(Map.of());
    }
}

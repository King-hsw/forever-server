package com.forever.server.moment;

import com.forever.server.auth.AuthPrincipal;
import com.forever.server.auth.Perm;
import com.forever.server.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 动态管理端：发布/删除/点赞/媒体上传。
 * 发布与上传需 moment:post 权限码；删除/点赞仅登录，删除在 Service 内再校验作者或 ADMIN。
 */
@Tag(name = "动态管理", description = "发布/删除动态、点赞、媒体上传，需 JWT 认证")
@RestController
@RequestMapping("/api/admin")
public class AdminMomentController {

    private final MomentService momentService;

    public AdminMomentController(MomentService momentService) {
        this.momentService = momentService;
    }

    @Perm("moment:post")
    @Operation(summary = "发布动态", description = "文本 ≤1000 字；图片 ≤9 张；文本/图片/音频/视频至少一项非空")
    @PostMapping("/moments")
    public ApiResponse<MomentResponse> create(Authentication authentication,
                                              @Valid @RequestBody MomentCreateRequest request) {
        return ApiResponse.ok(momentService.create(uidOf(authentication), request));
    }

    @Perm // 显式声明：仅需登录态，作者/ADMIN 校验在 Service 内
    @Operation(summary = "删除动态", description = "仅作者本人或 ADMIN 角色，否则 403；连同点赞与评论一并删除")
    @DeleteMapping("/moments/{id}")
    public ApiResponse<Void> delete(Authentication authentication, @PathVariable Long id) {
        momentService.delete(uidOf(authentication), id);
        return ApiResponse.ok();
    }

    @Perm // 显式声明：仅需登录态
    @Operation(summary = "点赞", description = "重复点赞幂等；返回最新点赞数与当前用户是否已赞")
    @PostMapping("/moments/{id}/like")
    public ApiResponse<MomentDtos.LikeResponse> like(Authentication authentication, @PathVariable Long id) {
        return ApiResponse.ok(momentService.like(uidOf(authentication), id));
    }

    @Perm // 显式声明：仅需登录态
    @Operation(summary = "取消点赞", description = "未点赞时幂等")
    @DeleteMapping("/moments/{id}/like")
    public ApiResponse<MomentDtos.LikeResponse> unlike(Authentication authentication, @PathVariable Long id) {
        return ApiResponse.ok(momentService.unlike(uidOf(authentication), id));
    }

    /** /api/admin/** 经 SecurityConfig 登录校验，此处 principal 必为 AuthPrincipal */
    private static long uidOf(Authentication authentication) {
        return ((AuthPrincipal) authentication.getPrincipal()).uid();
    }
}

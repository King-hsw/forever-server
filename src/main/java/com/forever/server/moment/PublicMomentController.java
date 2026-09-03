package com.forever.server.moment;

import com.forever.server.auth.AuthPrincipal;
import com.forever.server.comment.CommentAdminResponse;
import com.forever.server.comment.CommentCreateRequest;
import com.forever.server.comment.CommentResponse;
import com.forever.server.comment.CommentService;
import com.forever.server.common.ApiResponse;
import com.forever.server.common.PageParams;
import com.forever.server.common.PageResult;
import com.forever.server.common.Web;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * 动态时间线：公开（SSR、免登录），查看无需登录；
 * 发布/删除见 {@link AdminMomentController}（登录 + RBAC）。
 */
@Tag(name = "动态·公开接口", description = "访客查看动态与评论，无需登录")
@RestController
@RequiredArgsConstructor
public class PublicMomentController {

    private final MomentService momentService;
    private final CommentService commentService;
    private final AmapService amapService;

    @Operation(summary = "分页查看动态", description = "created_at 倒序；user 可选，只查该用户的动态；canDelete 按当前登录态计算")
    @GetMapping("/api/v1/moments")
    public ApiResponse<PageResult<MomentResponse>> list(
            @Parameter(description = "页码，从 1 开始", example = "1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数（最大 100）", example = "20") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "只查该用户的动态") @RequestParam(required = false) Long user,
            Authentication authentication) {
        page = PageParams.normalizePage(page);
        size = PageParams.normalizeSize(size);
        return ApiResponse.ok(momentService.page(viewerUid(authentication), user, page, size));
    }

    @Operation(summary = "逆地理编码", description = "高德 Web Service 逆地理；key 未配置或调用失败返回 {text:null}")
    @GetMapping("/api/v1/moments/geocode")
    public ApiResponse<MomentDtos.GeocodeResponse> geocode(
            @Parameter(description = "纬度") @RequestParam(required = false) Double lat,
            @Parameter(description = "经度") @RequestParam(required = false) Double lng) {
        return ApiResponse.ok(new MomentDtos.GeocodeResponse(
                lat == null || lng == null ? null : amapService.regeoText(lat, lng)));
    }

    @Operation(summary = "分页查看动态评论", description = "两层楼结构同文章/留言板评论；仅返回已过审评论")
    @GetMapping("/api/v1/moments/{id}/comments")
    public ApiResponse<PageResult<CommentResponse>> comments(
            @PathVariable Long id,
            @Parameter(description = "页码，从 1 开始", example = "1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数（最大 100）", example = "10") @RequestParam(defaultValue = "10") int size) {
        size = PageParams.normalizeSize(size);
        return ApiResponse.ok(commentService.pageByMoment(id, page, size));
    }

    @Operation(summary = "发表动态评论", description = "游客可评；带有效登录态时自动以 sys_user 资料身份发布（昵称/邮箱/主页，邮箱可为空）；限流 / 敏感词 / 审核规则与文章/留言板评论一致")
    @PostMapping("/api/v1/moments/{id}/comments")
    public ApiResponse<CommentAdminResponse> createComment(
            @PathVariable Long id,
            @Valid @RequestBody CommentCreateRequest request,
            HttpServletRequest httpRequest,
            Authentication authentication) {
        return ApiResponse.ok(commentService.createMoment(id, viewerUid(authentication), request, Web.clientIp(httpRequest)));
    }

    /**
     * 公开接口不强制登录：带有效 Bearer 返回其 uid，匿名 / 无效凭证返回空
     */
    private static Long viewerUid(Authentication authentication) {
        return Optional.ofNullable(authentication)
                .map(Authentication::getPrincipal)
                .filter(AuthPrincipal.class::isInstance)
                .map(p -> ((AuthPrincipal) p).uid())
                .orElse(null);
    }
}

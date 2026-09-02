package com.forever.server.comment;

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
import org.springframework.web.bind.annotation.*;

@Tag(name = "评论·公开接口", description = "访客查看与发表评论，无需登录")
@RestController
@RequiredArgsConstructor
public class PublicCommentController {

    private final CommentService commentService;

    @Operation(summary = "分页查看文章评论", description = "两层楼结构：根评论按时间倒序，楼内回复正序；仅返回已过审评论")
    @GetMapping("/api/v1/articles/{articleId}/comments")
    public ApiResponse<PageResult<CommentResponse>> list(
            @Parameter(description = "文章 id") @PathVariable Long articleId,
            @Parameter(description = "页码，从 1 开始", example = "1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数（最大 100）", example = "10") @RequestParam(defaultValue = "10") int size) {
        size = PageParams.normalizeSize(size);
        return ApiResponse.ok(commentService.pageByArticle(articleId, page, size));
    }

    @Operation(summary = "发表评论", description = """
            访客评论，同一 IP 限每 10 秒 1 条（可配 blog.comment.post-interval-seconds）；内容命中敏感词会被自动打码。
            parentId 不传为发根评论，传则为回复（root 自动归组）。
            是否先审后显由后台 blog.comment.auto-approve 配置决定。""")
    @PostMapping("/api/v1/comments")
    public ApiResponse<CommentAdminResponse> create(@Valid @RequestBody CommentCreateRequest request,
                                                    HttpServletRequest httpRequest) {
        String ip = Web.clientIp(httpRequest);
        return ApiResponse.ok(commentService.create(request, ip));
    }
}

package com.forever.server.comment;

import com.forever.server.common.ApiResponse;
import com.forever.server.common.PageParams;
import com.forever.server.common.PageResult;
import com.forever.server.common.Web;
import com.forever.server.setting.SiteConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 留言板。留言即 target_type=BOARD 的评论，
 * 敏感词打码 / 审核 / 嵌套回复 / IP 限流 / 邮件通知全部复用评论模块。
 */
@Tag(name = "留言板·公开接口", description = "访客查看与发表留言，无需登录")
@RestController
@RequiredArgsConstructor
public class BoardController {

    private final CommentService commentService;
    private final SiteConfigService siteConfig;

    public record BoardInfo(String title, String summary) {
    }

    @Operation(summary = "留言板信息", description = "标题与简介来自后台站点设置 board.title / board.summary")
    @GetMapping("/api/v1/board")
    public ApiResponse<BoardInfo> info() {
        return ApiResponse.ok(new BoardInfo(siteConfig.boardTitle(), siteConfig.boardSummary()));
    }

    @Operation(summary = "分页查看留言", description = "两层楼结构同文章评论；仅返回已过审留言")
    @GetMapping("/api/v1/board/messages")
    public ApiResponse<PageResult<CommentResponse>> list(
            @Parameter(description = "页码，从 1 开始", example = "1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数（最大 100）", example = "24") @RequestParam(defaultValue = "24") int size) {
        size = PageParams.normalizeSize(size);
        return ApiResponse.ok(commentService.pageByBoard(page, size));
    }

    @Operation(summary = "发表留言", description = "限流 / 敏感词 / 审核规则与文章评论一致")
    @PostMapping("/api/v1/board/messages")
    public ApiResponse<CommentAdminResponse> create(@Valid @RequestBody CommentCreateRequest request,
                                                    HttpServletRequest httpRequest) {
        return ApiResponse.ok(commentService.createBoard(request, Web.clientIp(httpRequest)));
    }
}

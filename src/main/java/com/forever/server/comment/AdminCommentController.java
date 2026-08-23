package com.forever.server.comment;

import com.forever.server.common.ApiResponse;
import com.forever.server.common.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "评论管理", description = "评论审核与维护，需 JWT 认证")
@RestController
@RequestMapping("/api/admin/comments")
public class AdminCommentController {

    private final CommentService commentService;

    public AdminCommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @Operation(summary = "评论列表", description = "管理端全量可见；status 可选 APPROVED / PENDING / REJECTED，不传查全部")
    @GetMapping
    public ApiResponse<PageResult<CommentAdminResponse>> page(
            @Parameter(description = "状态过滤", example = "PENDING")
            @RequestParam(required = false) String status,
            @Parameter(description = "页码，从 1 开始", example = "1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数（最大 100）", example = "20") @RequestParam(defaultValue = "20") int size) {
        size = Math.min(Math.max(size, 1), 100);
        status = status == null || status.isBlank() ? null : status;
        return ApiResponse.ok(commentService.pageAdmin(status, page, size));
    }

    @Operation(summary = "通过审核", description = "PENDING/REJECTED -> APPROVED，前台立即可见")
    @PutMapping("/{id}/approve")
    public ApiResponse<Void> approve(@PathVariable Long id) {
        commentService.approve(id);
        return ApiResponse.ok();
    }

    @Operation(summary = "拒绝显示", description = "-> REJECTED，前台不可见但保留记录")
    @PutMapping("/{id}/reject")
    public ApiResponse<Void> reject(@PathVariable Long id) {
        commentService.reject(id);
        return ApiResponse.ok();
    }

    @Operation(summary = "删除评论", description = "连同该评论楼中的所有回复一起删除")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        commentService.delete(id);
        return ApiResponse.ok();
    }
}

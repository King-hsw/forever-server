package com.forever.server.comment;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "管理端评论视图：比公开版多邮箱/IP/状态/所属文章")
public record CommentAdminResponse(
        Long id,
        Long articleId,
        @Schema(description = "文章标题") String articleTitle,
        Long parentId,
        Long rootId,
        String nickname,
        @Schema(description = "不对外展示") String email,
        String site,
        String content,
        @Schema(description = "APPROVED / PENDING / REJECTED") String status,
        String ip,
        LocalDateTime createdAt) {
}

package com.forever.server.comment;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "公开评论（两层楼结构）")
public record CommentResponse(
        Long id,
        @Schema(description = "所回复消息的 id（根评论为 null）") Long parentId,
        @Schema(description = "昵称") String nickname,
        @Schema(description = "头像 URL（由邮箱哈希生成）") String avatarUrl,
        @Schema(description = "个人主页，可为 null") String site,
        @Schema(description = "内容（敏感词已打码）") String content,
        @Schema(description = "评论时间") LocalDateTime createdAt,
        @Schema(description = "该评论下的回复，按时间正序") List<CommentResponse> replies,
        @Schema(description = "所回复的楼内回复昵称（仅回复楼内回复时非 null）") String parentNickname,
        @Schema(description = "所回复的楼内回复内容，前端展示引用") String parentContent) {

    /** 根评论（replies 待组装） */
    static CommentResponse root(Comment c, String avatarUrl) {
        return new CommentResponse(c.getId(), null, c.getNickname(), avatarUrl, c.getSite(),
                c.getContent(), c.getCreatedAt(), List.of(), null, null);
    }

    /** 回复（无下级）；parentNickname/parentContent 为所回复的楼内回复，回复根评论时为 null */
    static CommentResponse reply(Comment c, String avatarUrl, String parentNickname, String parentContent) {
        return new CommentResponse(c.getId(), c.getParentId(), c.getNickname(), avatarUrl, c.getSite(),
                c.getContent(), c.getCreatedAt(), null, parentNickname, parentContent);
    }
}

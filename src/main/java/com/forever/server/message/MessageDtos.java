package com.forever.server.message;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 消息中心小 DTO 集合。
 */
public final class MessageDtos {

    private MessageDtos() {
    }

    @Schema(description = "站内消息")
    public record MessageResponse(
            Long id,
            @Schema(description = "类型：COMMENT_REPLY-收到回复 / NEW_COMMENT-收到新评论") String type,
            String content,
            @Schema(description = "点击跳转链接") String sourceUrl,
            boolean isRead,
            LocalDateTime createdAt) {
    }

    @Schema(description = "未读消息数")
    public record UnreadCountResponse(long count) {
    }
}

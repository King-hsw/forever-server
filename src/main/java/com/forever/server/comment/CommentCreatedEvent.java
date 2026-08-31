package com.forever.server.comment;

/**
 * 评论落库后发布：通知渠道（邮件、Web Push、站内消息）各自订阅，互不影响。
 */
public record CommentCreatedEvent(
        Comment comment,
        Comment parent,
        String sourceTitle,
        String sourceUrl) {
}

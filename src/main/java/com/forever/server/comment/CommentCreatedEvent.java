package com.forever.server.comment;

/**
 * 新评论创建事件。评论落库后由 CommentService 发出；
 * 邮件通知等后续动作通过 @EventListener 接入，评论主流程不感知订阅者。
 *
 * @param comment      新评论（含打码后的内容）
 * @param parent       被回复的评论；根评论为 null
 * @param articleTitle 所属文章标题
 */
public record CommentCreatedEvent(Comment comment, Comment parent, String articleTitle) {
}

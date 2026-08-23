package com.forever.server.article;

/**
 * 文章发布事件。Core 在发布动作完成后发出，
 * 扩展能力（搜索索引、AI 摘要、通知等）通过 @EventListener 接入，不侵入 Core。
 *
 * @param articleId 文章 id
 * @param slug      URL 别名
 * @param title     标题
 */
public record ArticlePublishedEvent(Long articleId, String slug, String title) {
}

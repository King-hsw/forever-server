package com.forever.server.article;

/**
 * 文章下线事件（PUBLISHED -> DRAFT）。
 *
 * @param articleId 文章 id
 * @param slug      URL 别名
 */
public record ArticleUnpublishedEvent(Long articleId, String slug) {
}

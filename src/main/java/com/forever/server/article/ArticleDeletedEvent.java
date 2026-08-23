package com.forever.server.article;

/**
 * 文章软删除事件。订阅方据此清理自身数据（如搜索索引、统计记录）。
 *
 * @param articleId 文章 id
 * @param slug      URL 别名
 */
public record ArticleDeletedEvent(Long articleId, String slug) {
}

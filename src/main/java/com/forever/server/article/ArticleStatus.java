package com.forever.server.article;

/**
 * 文章状态：草稿 / 已发布
 */
public enum ArticleStatus {
    /**
     * 草稿，前台不可见
     */
    DRAFT,
    /**
     * 已发布，前台可见
     */
    PUBLISHED
}

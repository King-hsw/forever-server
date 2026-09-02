package com.forever.server.article;

/**
 * 内容类型。ARTICLE 与 PAGE 共用同一套内容模型：
 * 前者走时间线（/articles/{slug}），后者是独立页面（关于、友链等，/pages/{slug}）。
 * PAGE 不出现在公开文章列表中。
 */
public enum ArticleType {
    /**
     * 普通文章（默认），按发布时间进入时间线
     */
    ARTICLE,
    /**
     * 独立页面：关于我、友链等
     */
    PAGE
}

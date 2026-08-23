-- V9 : 文章模型扩展——类型与正文格式
-- type           : ARTICLE(时间线文章) / PAGE(独立页面)，为 /about 等单页复用同一内容模型
-- content_format : MARKDOWN / HTML，Core 只存取不渲染，客户端按格式处理

ALTER TABLE article
    ADD COLUMN type           VARCHAR(20) NOT NULL DEFAULT 'ARTICLE',
    ADD COLUMN content_format VARCHAR(20) NOT NULL DEFAULT 'MARKDOWN';

COMMENT ON COLUMN article.type IS '内容类型：ARTICLE-文章 / PAGE-独立页面';
COMMENT ON COLUMN article.content_format IS '正文格式：MARKDOWN / HTML';

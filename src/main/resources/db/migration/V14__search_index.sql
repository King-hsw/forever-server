-- V14 : 全局搜索 trigram 索引
-- 启用 pg_trgm，为标题/摘要/正文的 ILIKE 模糊匹配建 GIN 索引；
-- 相关度排序用的 word_similarity 同样依赖该扩展。

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_article_title_trgm   ON article USING gin (title gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_article_summary_trgm ON article USING gin (summary gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_article_content_trgm ON article USING gin (content gin_trgm_ops);

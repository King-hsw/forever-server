-- V5 : 文章-标签多对多
-- 无外键约束：关联完整性由应用层维护（增删时手动清理）
CREATE TABLE article_tag (
    article_id BIGINT NOT NULL,
    tag_id     BIGINT NOT NULL,
    PRIMARY KEY (article_id, tag_id)
);

CREATE INDEX idx_article_tag_tag ON article_tag(tag_id);

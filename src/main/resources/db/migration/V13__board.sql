-- V13 : 留言板独立于文章
-- 全新安装时 V8 已直接建为目标结构（target_type / target_id，无 article 外键），
-- 本脚本保留为存量库的迁移路径 + 索引兜底；所有语句幂等。

ALTER TABLE comment ADD COLUMN IF NOT EXISTS target_type VARCHAR(20) NOT NULL DEFAULT 'ARTICLE';

DROP INDEX IF EXISTS idx_comment_article;
CREATE INDEX IF NOT EXISTS idx_comment_target ON comment (target_type, target_id, created_at);

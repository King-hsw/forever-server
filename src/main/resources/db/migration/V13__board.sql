-- V13 : 留言板独立于文章
-- comment 增加 target_type（ARTICLE / BOARD），article_id 改名 target_id；
-- 留言板不再是一篇 type=PAGE 的文章，标题/简介放 sys_site_config（board.title / board.summary），
-- 由后台「站点设置」维护；BOARD 评论的 target_id 统一记 0。

ALTER TABLE comment DROP CONSTRAINT IF EXISTS comment_article_id_fkey;
ALTER TABLE comment ADD COLUMN target_type VARCHAR(20) NOT NULL DEFAULT 'ARTICLE';
ALTER TABLE comment RENAME COLUMN article_id TO target_id;

UPDATE comment SET target_type = 'BOARD', target_id = 0
 WHERE target_id IN (SELECT id FROM article WHERE slug = 'message');

DELETE FROM article WHERE slug = 'message';

DROP INDEX IF EXISTS idx_comment_article;
CREATE INDEX idx_comment_target ON comment (target_type, target_id, created_at);

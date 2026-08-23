-- V10 : 留言板种子数据
-- 留言板不单独建表：它是一篇 type=PAGE 的独立内容，
-- 访客留言即挂在它下面的评论，敏感词打码 / 审核 / 限流 / 邮件通知全部自动复用。
-- slug 固定为 message；已存在（管理员自行建过）则跳过。

INSERT INTO article (title, slug, summary, content, cover_image, category_id,
                     status, type, content_format, published_at)
VALUES ('留言板',
        'message',
        '对网站有任何建议、想法，或者只是想打个招呼，都欢迎在这里留言。',
        E'# 留言板\n\n对网站有任何建议、想法，或者只是想打个招呼，都欢迎在这里留言。\n',
        NULL,
        NULL,
        'PUBLISHED',
        'PAGE',
        'MARKDOWN',
        CURRENT_TIMESTAMP)
ON CONFLICT (slug) DO NOTHING;

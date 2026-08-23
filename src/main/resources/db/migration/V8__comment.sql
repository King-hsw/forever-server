-- V8 : 评论系统 + 敏感词库
-- comment        : 访客评论，两层楼结构（parent_id 直接回复 / root_id 归属根评论）
-- sensitive_word : 敏感词库，命中替换打码

CREATE TABLE comment (
    id         BIGSERIAL PRIMARY KEY,
    article_id BIGINT       NOT NULL REFERENCES article (id),
    parent_id  BIGINT       REFERENCES comment (id),
    root_id    BIGINT,
    nickname   VARCHAR(50)  NOT NULL,
    email      VARCHAR(100) NOT NULL,                 -- 不公开展示，仅用于头像与回复通知
    site       VARCHAR(200),
    content    VARCHAR(500) NOT NULL,
    status     VARCHAR(20)  NOT NULL DEFAULT 'APPROVED',  -- APPROVED / PENDING / REJECTED
    ip         VARCHAR(45),
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_comment_article ON comment (article_id, created_at);
CREATE INDEX idx_comment_root ON comment (root_id);

CREATE TABLE sensitive_word (
    id          BIGSERIAL PRIMARY KEY,
    word        VARCHAR(100) NOT NULL UNIQUE,
    replacement VARCHAR(100) NOT NULL DEFAULT '***',
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

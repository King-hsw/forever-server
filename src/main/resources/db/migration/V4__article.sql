-- V4 : 文章表（软删）
CREATE TABLE article (
    id           BIGSERIAL PRIMARY KEY,
    title        VARCHAR(200) NOT NULL,
    slug         VARCHAR(200) NOT NULL UNIQUE,
    summary      VARCHAR(500),
    content      TEXT         NOT NULL,
    cover_image  VARCHAR(500),
    category_id  BIGINT       REFERENCES category(id),
    status       VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    view_count   BIGINT       NOT NULL DEFAULT 0,
    published_at TIMESTAMP,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_article_category ON article(category_id);
CREATE INDEX idx_article_status_published ON article(status, published_at DESC);

-- V6 : RSS 订阅功能
-- rss_feed   : 订阅的博客源（管理端维护）
-- rss_item   : 从源抓取到的文章条目，按 (feed_id, link) 幂等去重

CREATE TABLE rss_feed (
    id              BIGSERIAL PRIMARY KEY,
    title           VARCHAR(200),                -- 站点名称，抓取时自动回填
    site_url        VARCHAR(500) NOT NULL,       -- 博客主页，前端展示跳转用
    feed_url        VARCHAR(500) NOT NULL UNIQUE,-- RSS/Atom 地址
    description     VARCHAR(500),
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    last_fetched_at TIMESTAMP,
    last_error      VARCHAR(500),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE rss_item (
    id           BIGSERIAL PRIMARY KEY,
    feed_id      BIGINT       NOT NULL,       -- 逻辑关联 rss_feed.id，无外键；删除源时应用层同步删条目
    link         VARCHAR(500) NOT NULL,
    title        VARCHAR(500) NOT NULL,
    summary      VARCHAR(1000),
    published_at TIMESTAMP,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (feed_id, link)
);

CREATE INDEX idx_rss_item_published ON rss_item (published_at DESC);

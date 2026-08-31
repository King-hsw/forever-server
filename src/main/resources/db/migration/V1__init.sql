-- V1 : 初始基线（原 V1–V25 增量脚本合并后的净结构，全新环境唯一入口）
-- 说明：
--   * 端点级权限码不在此处播种，由 PermissionAutoRegistrar 启动时按 @Perm 注解自动注册
--   * AdminInitializer 首次启动时建管理员账号并挂 ADMIN 角色（内置角色/权限种子除外）
--   * 无外键约束：跨表引用完整性由应用层维护

CREATE TABLE sys_user (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR(50)  NOT NULL UNIQUE,
    password    VARCHAR(100) NOT NULL,
    nickname    VARCHAR(50),
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    email       VARCHAR(100),
    avatar_url  VARCHAR(255),
    site        VARCHAR(200),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE category (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(50)  NOT NULL UNIQUE,
    slug       VARCHAR(100) NOT NULL UNIQUE,
    sort       INT          NOT NULL DEFAULT 0,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE tag (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE article (
    id             BIGSERIAL PRIMARY KEY,
    title          VARCHAR(200) NOT NULL,
    slug           VARCHAR(200) NOT NULL UNIQUE,
    summary        VARCHAR(500),
    content        TEXT         NOT NULL,
    cover_image    VARCHAR(500),
    category_id    BIGINT,                        -- 逻辑关联 category.id，无外键约束
    type           VARCHAR(20)  NOT NULL DEFAULT 'ARTICLE',    -- ARTICLE(时间线文章) / PAGE(独立页面)
    content_format VARCHAR(20)  NOT NULL DEFAULT 'MARKDOWN',   -- MARKDOWN / HTML，Core 只存取不渲染
    status         VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    view_count     BIGINT       NOT NULL DEFAULT 0,
    published_at   TIMESTAMP,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_article_category ON article(category_id);
CREATE INDEX idx_article_status_published ON article(status, published_at DESC);

CREATE TABLE article_tag (
    article_id BIGINT NOT NULL,
    tag_id     BIGINT NOT NULL,
    PRIMARY KEY (article_id, tag_id)
);

CREATE INDEX idx_article_tag_tag ON article_tag(tag_id);

CREATE TABLE rss_feed (
    id              BIGSERIAL PRIMARY KEY,
    title           VARCHAR(200),
    site_url        VARCHAR(500) NOT NULL,
    feed_url        VARCHAR(500) NOT NULL UNIQUE,
    description     VARCHAR(500),
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    last_fetched_at TIMESTAMP,
    last_error      VARCHAR(500),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE rss_item (
    id           BIGSERIAL PRIMARY KEY,
    feed_id      BIGINT       NOT NULL,   -- 逻辑关联 rss_feed.id；删除源时应用层同步删条目
    link         VARCHAR(500) NOT NULL,
    title        VARCHAR(500) NOT NULL,
    summary      VARCHAR(1000),
    published_at TIMESTAMP,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (feed_id, link)
);

CREATE INDEX idx_rss_item_published ON rss_item (published_at DESC);

CREATE TABLE friend_link (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    site_url      VARCHAR(500) NOT NULL UNIQUE,
    icon_url      VARCHAR(500),
    description   VARCHAR(200),
    contact       VARCHAR(200),
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING / APPROVED / REJECTED
    reject_reason VARCHAR(200),
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at   TIMESTAMP
);

-- 评论，两层楼结构（parent_id 直接回复 / root_id 归属根评论），跨目标复用：
-- target_type: ARTICLE / BOARD / MOMENT；BOARD 的 target_id 固定为 0
CREATE TABLE comment (
    id          BIGSERIAL PRIMARY KEY,
    target_type VARCHAR(20)  NOT NULL DEFAULT 'ARTICLE',
    target_id   BIGINT       NOT NULL,
    parent_id   BIGINT,
    root_id     BIGINT,
    nickname    VARCHAR(50)  NOT NULL,
    email       VARCHAR(100),                  -- 不公开展示，仅用于头像与回复通知；登录用户可空
    site        VARCHAR(200),
    content     VARCHAR(500) NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'APPROVED',  -- APPROVED / PENDING / REJECTED
    ip          VARCHAR(45),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_comment_target ON comment (target_type, target_id, created_at);
CREATE INDEX idx_comment_root ON comment (root_id);

CREATE TABLE sensitive_word (
    id          BIGSERIAL PRIMARY KEY,
    word        VARCHAR(100) NOT NULL UNIQUE,
    replacement VARCHAR(100) NOT NULL DEFAULT '***',
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sys_action_log (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR(50),                   -- 后台操作人；匿名请求为 NULL
    method      VARCHAR(10)  NOT NULL,
    path        VARCHAR(500) NOT NULL,
    status      INT          NOT NULL,
    ip          VARCHAR(45),
    duration_ms BIGINT,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_action_log_created ON sys_action_log (created_at DESC);
CREATE INDEX idx_action_log_username ON sys_action_log (username, created_at DESC);

CREATE TABLE sys_site_config (
    config_key   VARCHAR(100) PRIMARY KEY,
    config_value VARCHAR(200) NOT NULL,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- RBAC：sys_user 不变，用户-角色多对多；角色与权限均有主数据表，后台可调配「角色 -> 权限」矩阵
CREATE TABLE sys_role (
    id         BIGSERIAL PRIMARY KEY,
    code       VARCHAR(30)  NOT NULL UNIQUE,
    name       VARCHAR(50)  NOT NULL,
    remark     VARCHAR(200),
    built_in   BOOLEAN      NOT NULL DEFAULT false,   -- 内置角色不可删除
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sys_permission (
    id         BIGSERIAL PRIMARY KEY,
    code       VARCHAR(50)  NOT NULL UNIQUE,          -- 如 moment:post，代码中引用
    name       VARCHAR(50)  NOT NULL,
    module     VARCHAR(30)  NOT NULL DEFAULT '其他',   -- 后台分组展示用
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE sys_role_permission (
    role_id       BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id)
);

-- 内置角色
INSERT INTO sys_role (code, name, remark, built_in) VALUES
    ('ADMIN',  '管理员', '站长，拥有全部权限', true),
    ('MEMBER', '成员',   '可发表朋友圈，可访问书城', true),
    ('USER',   '普通用户', '可访问书城', true);

-- 内置权限点（端点级权限码由 PermissionAutoRegistrar 启动时自动注册，不在此播种）
INSERT INTO sys_permission (code, name, module) VALUES
    ('admin:access',  '后台访问',   '系统'),
    ('moment:post',   '发表朋友圈', '朋友圈'),
    ('store:access',  '书城访问',   '书城'),
    ('store:manage',  '书城管理',   '书城');

-- 默认授权矩阵
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON
    (r.code = 'ADMIN' AND p.code IN ('admin:access', 'moment:post', 'store:access', 'store:manage'))
 OR (r.code = 'MEMBER' AND p.code IN ('moment:post', 'store:access'))
 OR (r.code = 'USER' AND p.code = 'store:access');

-- 登录态双 Token（不透明随机串，落库可吊销）：只存 SHA-256 哈希，删行即登出
CREATE TABLE sys_auth_token (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT      NOT NULL,           -- 逻辑关联 sys_user.id；删号时应用层清理
    access_token      VARCHAR(64) NOT NULL UNIQUE,    -- 短效（2 小时），Authorization: Bearer
    refresh_token     VARCHAR(64) NOT NULL UNIQUE,    -- 长效（30 天），仅用于 /api/auth/refresh
    access_expires_at  TIMESTAMP  NOT NULL,
    refresh_expires_at TIMESTAMP NOT NULL,
    created_at        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_auth_token_user ON sys_auth_token (user_id);

CREATE TABLE moment (
    id          BIGSERIAL PRIMARY KEY,
    uid         BIGINT        NOT NULL,               -- sys_user.id
    content     VARCHAR(1000) NOT NULL DEFAULT '',
    media       TEXT          NOT NULL DEFAULT '{}',   -- JSON: {"images":[...],"audio":...,"video":...}
    location    VARCHAR(100),
    lat         DECIMAL(10,7),
    lng         DECIMAL(10,7),
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_moment_uid ON moment (uid);
CREATE INDEX idx_moment_created ON moment (created_at);

CREATE TABLE moment_like (
    moment_id   BIGINT    NOT NULL,
    uid         BIGINT    NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (moment_id, uid)
);

CREATE INDEX idx_moment_like_uid ON moment_like (uid);

-- Web Push 订阅：浏览器 pushManager.subscribe 结果，endpoint 全局唯一，重复订阅幂等 upsert
CREATE TABLE push_subscription (
    id                BIGSERIAL PRIMARY KEY,
    endpoint          VARCHAR(1000) NOT NULL UNIQUE,  -- 推送服务分配的订阅地址
    p256dh            VARCHAR(200)  NOT NULL,         -- 客户端 ECDH 公钥（Base64URL）
    auth              VARCHAR(200)  NOT NULL,         -- 鉴权密钥（Base64URL）
    user_id           BIGINT,                         -- 绑定的登录用户；游客订阅为 null
    email             VARCHAR(200),                   -- 归属邮箱，评论回复定向推送按此命中
    added_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_sent_at      TIMESTAMP,                      -- 最近一次推送成功时间
    last_delivered_at TIMESTAMP                      -- SW 最近一次送达回执时间（排查失效订阅）
);

CREATE INDEX idx_push_subscription_email ON push_subscription(email);
CREATE INDEX idx_push_subscription_user_id ON push_subscription(user_id);

-- pg_trgm：标题/摘要/正文 ILIKE 模糊匹配的 GIN 索引 + word_similarity 相关度排序
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_article_title_trgm   ON article USING gin (title gin_trgm_ops);
CREATE INDEX idx_article_summary_trgm ON article USING gin (summary gin_trgm_ops);
CREATE INDEX idx_article_content_trgm ON article USING gin (content gin_trgm_ops);

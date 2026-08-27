-- V20 : 朋友圈（动态）
-- moment      : 动态正文 + 媒体（JSON）+ 地点
-- moment_like : 点赞，(moment_id, uid) 唯一
-- comment 表不动：target_type 新增 MOMENT，target_id = moment.id，复用评论模块

CREATE TABLE moment (
    id          BIGSERIAL PRIMARY KEY,
    uid         BIGINT        NOT NULL,                 -- sys_user.id
    content     VARCHAR(1000) NOT NULL DEFAULT '',
    media       TEXT          NOT NULL DEFAULT '{}',     -- JSON: {"images":[...],"audio":...,"video":...}
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

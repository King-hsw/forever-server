-- ============================================================
-- 消息中心：站内消息收件箱（单向通知），收件人即 sys_user 账号
-- ============================================================
CREATE TABLE message (
    id          BIGSERIAL   PRIMARY KEY,
    user_id     BIGINT      NOT NULL,                -- 收件人
    type        VARCHAR(30) NOT NULL,                 -- COMMENT_REPLY / NEW_COMMENT
    content     VARCHAR(500) NOT NULL,                -- 摘要文案
    source_url  VARCHAR(200) NOT NULL,                -- 点击跳转链接（含锚点）
    is_read     BOOLEAN     NOT NULL DEFAULT FALSE,
    deleted     BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_message_user ON message (user_id, created_at DESC);

-- 评论补记归属账号：登录用户发评时写入（游客为空），供站内消息精确定位收件人
ALTER TABLE comment ADD COLUMN user_id BIGINT;

-- V11 : 操作审计日志
-- 记录后台写操作（发布文章、删评论等）与登录成败，用于安全追溯与误操作排查。
-- 只增不改不删（清理策略以后按量归档），查询走管理端 API。

CREATE TABLE sys_action_log (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR(50),                 -- 后台操作人；匿名请求为 NULL
    method      VARCHAR(10)  NOT NULL,
    path        VARCHAR(500) NOT NULL,
    status      INT          NOT NULL,       -- HTTP 响应码，401/500 一目了然
    ip          VARCHAR(45),
    duration_ms BIGINT,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_action_log_created ON sys_action_log (created_at DESC);
CREATE INDEX idx_action_log_username ON sys_action_log (username, created_at DESC);

-- V17 : 登录态改为双 Token（不透明随机串，落库可吊销）
-- access_token  : 短效（2 小时），随请求头 Authorization: Bearer 提交
-- refresh_token : 长效（30 天），仅用于 POST /api/auth/refresh 换新令牌对
-- 库中只存 SHA-256 哈希，明文只在签发时返回一次；删行即登出。

CREATE TABLE sys_auth_token (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT      NOT NULL,              -- 逻辑关联 sys_user.id，无外键；删号时应用层清理
    access_token      VARCHAR(64) NOT NULL UNIQUE,
    refresh_token     VARCHAR(64) NOT NULL UNIQUE,
    access_expires_at  TIMESTAMP  NOT NULL,
    refresh_expires_at TIMESTAMP NOT NULL,
    created_at        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_auth_token_user ON sys_auth_token (user_id);

-- V23 : Web Push 订阅
-- 浏览器推送订阅（前端 pushManager.subscribe 的结果），endpoint 全局唯一，重复订阅幂等 upsert；
-- user_id 绑定登录用户（管理员带登录态订阅时补记），游客订阅为 null

CREATE TABLE push_subscription (
    id                BIGSERIAL PRIMARY KEY,
    endpoint          VARCHAR(1000) NOT NULL UNIQUE, -- 推送服务分配的订阅地址
    p256dh            VARCHAR(200)  NOT NULL,        -- 客户端 ECDH 公钥（Base64URL）
    auth              VARCHAR(200)  NOT NULL,        -- 鉴权密钥（Base64URL）
    user_id           BIGINT,                        -- 绑定的登录用户；游客订阅为 null
    added_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_sent_at      TIMESTAMP,                     -- 最近一次推送成功时间
    last_delivered_at TIMESTAMP                      -- SW 最近一次送达回执时间（排查失效订阅）
);

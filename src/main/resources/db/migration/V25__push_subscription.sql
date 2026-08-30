-- V25 : Web Push 订阅
-- 浏览器推送订阅（前端 pushManager.subscribe 的结果），endpoint 全局唯一，重复订阅幂等 upsert；
-- user_id 绑定登录用户（管理员带登录态订阅时补记），游客订阅为 null；
-- email 归属邮箱（登录用户记资料邮箱、游客评论后上报），评论回复按其定向推送
-- 注：曾以 V23/V24 编号发布并与业务迁移撞号，老库清 history 后由本迁移幂等接管，故用 IF NOT EXISTS

CREATE TABLE IF NOT EXISTS push_subscription (
    id                BIGSERIAL PRIMARY KEY,
    endpoint          VARCHAR(1000) NOT NULL UNIQUE, -- 推送服务分配的订阅地址
    p256dh            VARCHAR(200)  NOT NULL,        -- 客户端 ECDH 公钥（Base64URL）
    auth              VARCHAR(200)  NOT NULL,        -- 鉴权密钥（Base64URL）
    user_id           BIGINT,                        -- 绑定的登录用户；游客订阅为 null
    email             VARCHAR(200),                  -- 归属邮箱，定向推送（评论回复）按此命中
    added_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_sent_at      TIMESTAMP,                     -- 最近一次推送成功时间
    last_delivered_at TIMESTAMP                      -- SW 最近一次送达回执时间（排查失效订阅）
);

CREATE INDEX IF NOT EXISTS idx_push_subscription_email ON push_subscription (email);
CREATE INDEX IF NOT EXISTS idx_push_subscription_user_id ON push_subscription (user_id);

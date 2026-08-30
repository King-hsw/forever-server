-- V24 : push_subscription 增加邮箱归属（定向推送）
-- 评论通知按邮箱定向：游客在评论成功后上报订阅与评论邮箱（subscribe 接口可选 email）；
-- 登录用户订阅时后端自动记录 sys_user.email，两个通道均可命中

ALTER TABLE push_subscription ADD COLUMN email VARCHAR(200);

CREATE INDEX idx_push_subscription_email ON push_subscription (email);
CREATE INDEX idx_push_subscription_user_id ON push_subscription (user_id);

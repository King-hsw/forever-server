-- 发件人地址回迁 yml：固定为 SMTP 登录账号（BLOG_MAIL_USERNAME，同域发信 163 必过）：
-- 清掉后台「站点设置」里的 comment.from-email 旧值，避免残留误导后续排查
DELETE FROM sys_site_config
WHERE config_key = 'comment.from-email';

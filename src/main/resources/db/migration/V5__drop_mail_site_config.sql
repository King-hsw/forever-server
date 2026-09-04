-- 邮件 SMTP（mail.*）回迁 yml/env（spring.mail / BLOG_MAIL_*）：
-- 清掉后台「站点设置」里的旧值，避免残留误导后续排查
DELETE FROM sys_site_config
WHERE config_key IN (
    'mail.host',
    'mail.port',
    'mail.username',
    'mail.password',
    'mail.ssl'
);

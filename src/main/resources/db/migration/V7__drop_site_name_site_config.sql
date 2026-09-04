-- 站点名称回迁 yml（blog.site.name / BLOG_SITE_NAME，邮件 From 显示名与 RSS 标题用）：
-- 清掉后台「站点设置」里的 site.name 旧值，避免残留误导后续排查
DELETE FROM sys_site_config
WHERE config_key = 'site.name';

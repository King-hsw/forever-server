-- 高德 key 回迁 yml/env（blog.moments.amap.key / BLOG_MOMENTS_AMAP_KEY）：
-- 清掉后台「站点设置」里的旧值；部署前把 config_value 捞出填进 .env
DELETE FROM sys_site_config
WHERE config_key = 'moments.amapKey';

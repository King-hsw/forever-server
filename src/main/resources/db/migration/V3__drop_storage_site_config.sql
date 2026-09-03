-- 文件存储配置（storage.*）回迁 yml/env（blog.storage，application-prod.yml）：
-- 清掉后台「站点设置」里的旧值，避免残留误导后续排查
DELETE FROM sys_site_config
WHERE config_key IN (
    'storage.endpoint',
    'storage.access-key',
    'storage.secret-key',
    'storage.bucket',
    'storage.presign-ttl'
);

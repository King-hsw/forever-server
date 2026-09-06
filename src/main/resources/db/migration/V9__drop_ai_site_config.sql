-- AI 概要配置（ai.*）回迁 yml/env（spring.ai.openai.*，application.yml）：
-- 清掉后台「站点设置」里的旧值，避免残留误导后续排查
DELETE FROM sys_site_config
WHERE config_key IN (
    'ai.summary-enabled',
    'ai.api-key',
    'ai.base-url',
    'ai.model'
);

-- V12 : 站点动态配置
-- 后台控制台可实时调整的运行参数（如评论限流间隔），KV 结构便于后续扩展。
-- 数据库值优先于 application.yml；yml 仅作为首次部署的默认值来源。

CREATE TABLE sys_site_config (
    config_key   VARCHAR(100) PRIMARY KEY,
    config_value VARCHAR(200) NOT NULL,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

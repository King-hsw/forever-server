package com.forever.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 启动期必要配置（blog.*）。仅保留应用启动或基础设施层面必需的项：
 * 初始管理员；dev 用 application.yml，
 * prod 必须通过环境变量覆盖（见 application-prod.yml）。
 * 业务运行参数（评论策略、站点地址、文件存储等）已迁至后台「站点设置」
 * （sys_site_config 表，PUT /api/admin/settings 实时调整），存储的 yml/环境变量仅作兜底默认值。
 */
@ConfigurationProperties(prefix = "blog")
public record BlogProperties(Admin admin) {

    /** 单管理员初始账密，首次启动时写入 sys_user */
    public record Admin(String username, String password) {
    }
}

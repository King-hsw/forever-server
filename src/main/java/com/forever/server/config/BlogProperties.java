package com.forever.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 启动期必要配置（blog.*）。仅保留应用启动或基础设施层面必需的项：
 * JWT 密钥、初始管理员、上传目录；dev 用 application.yml，
 * prod 必须通过环境变量覆盖（见 application-prod.yml）。
 * 业务运行参数（评论策略、站点地址等）已迁至后台「站点设置」
 * （sys_site_config 表，PUT /api/admin/settings 实时调整），不再走 yml。
 */
@ConfigurationProperties(prefix = "blog")
public record BlogProperties(Jwt jwt, Admin admin, Upload upload) {

    /** HS256 密钥（≥32 字节）与 token 有效期 */
    public record Jwt(String secret, long expireHours) {
    }

    /** 单管理员初始账密，首次启动时写入 sys_user */
    public record Admin(String username, String password) {
    }

    /** 上传文件本地存储根目录 */
    public record Upload(String baseDir) {
    }
}

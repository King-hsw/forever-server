package com.forever.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * blog.* 配置项。dev 使用 application.yml 默认值；
 * prod 必须通过环境变量覆盖（见 application-prod.yml）。
 */
@ConfigurationProperties(prefix = "blog")
public record BlogProperties(Jwt jwt, Admin admin, Upload upload, Cors cors, Site site, Comment comment) {

    /** HS256 密钥（≥32 字节）与 token 有效期 */
    public record Jwt(String secret, long expireHours) {
    }

    /** 单管理员初始账密，首次启动时写入 sys_user */
    public record Admin(String username, String password) {
    }

    /** 上传文件本地存储根目录 */
    public record Upload(String baseDir) {
    }

    /** CORS 白名单，供已有前端跨域调用 */
    public record Cors(List<String> allowedOrigins) {
    }

    /** 站点对外地址，用于生成 RSS feed 等绝对链接 */
    public record Site(String url) {
    }

    /** 评论策略：是否直接过审、邮件通知开关与发件人、同 IP 发评最小间隔秒数 */
    public record Comment(boolean autoApprove,
                          boolean notifyMail,
                          String ownerEmail,
                          String fromEmail,
                          Long postIntervalSeconds) {
    }
}

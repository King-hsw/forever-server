package com.forever.server.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 文件存储兜底配置（blog.storage）：仅在站点设置未配置对应项时生效，
 * 站点设置（sys_site_config 的 storage.*，后台实时调整）优先——见 {@link StorageSettings}。
 *
 * @param endpoint      对象存储的 S3 API 地址（默认端口 9000），如 http://127.0.0.1:9000
 * @param accessKey     访问密钥
 * @param secretKey     秘密密钥
 * @param bucket        存储桶名，缺失时首次使用自动创建
 * @param presignTtl    预签名 URL 有效期（下载 302 与直传 PUT 共用，默认 15 分钟）
 * @param tmpExpireDays 直传暂存前缀 tmp/ 的生命周期过期天数（默认 1 天，未发布即自动回收）
 * @param publicRead    公开读模式（默认 false）：对 moment/ 与 avatar/ 前缀安装匿名只读桶策略，
 *                      下载 302 改跳固定直链，浏览器/CDN 缓存可长期命中；小带宽服务器建议开启
 */
@ConfigurationProperties(prefix = "blog.storage")
public record StorageProperties(String endpoint, String accessKey, String secretKey, String bucket,
                                Duration presignTtl, Integer tmpExpireDays, Boolean publicRead) {

    public StorageProperties {
        presignTtl = presignTtl == null ? Duration.ofMinutes(15) : presignTtl;
        tmpExpireDays = tmpExpireDays == null || tmpExpireDays < 1 ? 1 : tmpExpireDays;
        publicRead = Boolean.TRUE.equals(publicRead);
    }

    public boolean isPublicRead() {
        return publicRead;
    }
}

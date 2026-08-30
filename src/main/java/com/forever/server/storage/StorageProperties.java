package com.forever.server.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 文件存储内置默认值：仓库与 yml 不再预置存储配置，生效值统一在后台「站点设置」配置
 * （sys_site_config 的 storage.*，实时调整）——见 {@link StorageSettings}；
 * 本类仅承载未配置时的回落默认值，blog.storage.* 前缀保留可绑定，供个别环境需要时自行覆盖。
 *
 * @param endpoint   对象存储的 S3 API 地址（默认端口 9000），如 http://127.0.0.1:9000；无内置值。
 *                   必须为浏览器可达的公网地址——文件读取直接走该直链，不经应用
 * @param accessKey  访问密钥；无内置值
 * @param secretKey  秘密密钥；无内置值
 * @param bucket     存储桶名（公开桶），缺失时首次使用自动创建；无内置值
 * @param presignTtl 预签名 URL 有效期（直传 PUT 与分片共用，默认 15 分钟）
 */
@ConfigurationProperties(prefix = "blog.storage")
public record StorageProperties(String endpoint, String accessKey, String secretKey, String bucket,
                                Duration presignTtl) {

    public StorageProperties {
        presignTtl = presignTtl == null ? Duration.ofMinutes(15) : presignTtl;
    }
}

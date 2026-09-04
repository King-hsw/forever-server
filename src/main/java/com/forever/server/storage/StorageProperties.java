package com.forever.server.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 文件存储配置（RustFS，S3 兼容）：yml 是唯一来源（blog.storage.*），
 * 本地/生产均由环境变量注入（BLOG_STORAGE_*，值在各自 .env，见 application.yml）；
 * 启动时构建一次客户端，改配置 = 改 env 重启。
 *
 * @param endpoint   对象存储 S3 API 地址（默认端口 9000），如 https://s3.example.com；
 *                   必须为浏览器可达的公网地址——文件读取走直链、预签名上传 URL 也由浏览器直连
 * @param publicBaseUrl 对象公开直链域名（含 scheme），仅用于拼直链；可选，留空则用 endpoint（无 CDN 场景 / 测试环境）
 * @param accessKey  访问密钥
 * @param secretKey  秘密密钥
 * @param bucket     存储桶名；须已建好并设为公开读，应用不代管桶
 * @param presignTtl 预签名 URL 有效期（直传 PUT 与分片共用，默认 15 分钟）
 */
@ConfigurationProperties(prefix = "blog.storage")
public record StorageProperties(String endpoint, String publicBaseUrl, String accessKey, String secretKey,
                                String bucket, Duration presignTtl) {

    public StorageProperties {
        presignTtl = presignTtl == null ? Duration.ofMinutes(15) : presignTtl;
    }

    /**
     * 启动期校验（由 RustFsStorageService 构造时调用）：缺项或格式错误直接启动失败
     */
    public void validate() {
        if (isBlank(endpoint) || isBlank(accessKey) || isBlank(secretKey) || isBlank(bucket)) {
            throw new IllegalStateException(
                    "文件存储配置不完整（blog.storage）：endpoint / access-key / secret-key / bucket 均必填");
        }
        if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
            throw new IllegalStateException("blog.storage.endpoint 必须以 http:// 或 https:// 开头：" + endpoint);
        }
        if (!isBlank(publicBaseUrl) && !publicBaseUrl.startsWith("http://") && !publicBaseUrl.startsWith("https://")) {
            throw new IllegalStateException("blog.storage.public-base-url 必须以 http:// 或 https:// 开头：" + publicBaseUrl);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}

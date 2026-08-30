package com.forever.server.storage;

import com.forever.server.common.BizException;
import com.forever.server.common.ErrorCode;
import com.forever.server.setting.SiteConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 文件存储生效配置：统一以后台「站点设置」（sys_site_config 的 storage.*）为准，
 * 未配置的项回落 {@link StorageProperties} 内置默认值。
 * 每次读取都取当前值——后台修改存储配置立即生效，无需重启。
 */
@Slf4j
@Component
public class StorageSettings {

    /** 构建 S3 客户端与桶预配（建桶/匿名只读策略）所依据的目标元组，任一项变化即触发重建 */
    public record S3Target(String endpoint, String accessKey, String secretKey, String bucket) {
    }

    private final SiteConfigService siteConfig;
    private final StorageProperties props;

    public StorageSettings(SiteConfigService siteConfig, StorageProperties props) {
        this.siteConfig = siteConfig;
        this.props = props;
    }

    public String endpoint() {
        return siteConfig.getString(SiteConfigService.STORAGE_ENDPOINT, props.endpoint());
    }

    public String accessKey() {
        return siteConfig.getString(SiteConfigService.STORAGE_ACCESS_KEY, props.accessKey());
    }

    public String secretKey() {
        return siteConfig.getString(SiteConfigService.STORAGE_SECRET_KEY, props.secretKey());
    }

    public String bucket() {
        return siteConfig.getString(SiteConfigService.STORAGE_BUCKET, props.bucket());
    }

    public Duration presignTtl() {
        String value = siteConfig.getString(SiteConfigService.STORAGE_PRESIGN_TTL, null);
        if (value == null) {
            return props.presignTtl();
        }
        try {
            return DurationStyle.detectAndParse(value);
        } catch (IllegalArgumentException e) {
            log.warn("site config storage.presign-ttl={} 非法，回落 {}", value, props.presignTtl());
            return props.presignTtl();
        }
    }

    /**
     * 当前生效的存储目标；连接信息任一缺失即抛业务异常。
     * 存储配置允许启动时残缺（应用正常起，仅上传/下载报错，后台补齐即恢复），故不做启动期校验。
     */
    public S3Target s3Target() {
        String endpoint = endpoint();
        String accessKey = accessKey();
        String secretKey = secretKey();
        String bucket = bucket();
        if (isBlank(endpoint) || isBlank(accessKey) || isBlank(secretKey) || isBlank(bucket)) {
            throw new BizException(ErrorCode.INTERNAL_ERROR,
                    "对象存储配置不完整：需要 endpoint / access-key / secret-key / bucket"
                            + "（后台站点设置 → 存储）");
        }
        return new S3Target(endpoint, accessKey, secretKey, bucket);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}

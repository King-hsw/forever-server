package com.forever.server.storage;

import com.forever.server.common.BizException;
import com.forever.server.common.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.BucketLifecycleConfiguration;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ExpirationStatus;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.LifecycleExpiration;
import software.amazon.awssdk.services.s3.model.LifecycleRule;
import software.amazon.awssdk.services.s3.model.LifecycleRuleFilter;
import software.amazon.awssdk.services.s3.model.MetadataDirective;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutBucketLifecycleConfigurationRequest;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;

/**
 * RustFS 存储（S3 兼容，AWS SDK v2 接入，见 docs.rustfs.com/zh/developer/sdk/java）：
 * path-style + us-east-1 是 RustFS 的固定要求。S3 客户端按 {@link StorageSettings} 的
 * 当前目标元组惰性构建——后台修改存储配置（endpoint/密钥/桶/过期天数/公开读）后，
 * 下次使用自动重建并幂等预配桶（建桶、tmp/ 生命周期、公开读策略），无需重启。
 * 读取走 UploadsController 的 302（预签名或公开读固定直链），直传收口走 stat/copy，本类只管写删与签发。
 */
@Slf4j
@Service
public class RustFsStorageService implements StorageService {

    /** 生命周期规则 id，幂等安装时用于判重 */
    private static final String TMP_LIFECYCLE_RULE_ID = "expire-tmp";

    private final StorageSettings settings;

    /** S3 客户端 + 预签名器 + 构建时所依据的目标元组（元组变化即重建） */
    private record Bundle(StorageSettings.S3Target target, S3Client s3, S3Presigner presigner) {
    }

    private volatile Bundle bundle;

    public RustFsStorageService(StorageSettings settings) {
        this.settings = settings;
    }

    /** 获取与当前配置一致的客户端；双检锁避免每次调用都进入同步块 */
    private Bundle bundle() {
        StorageSettings.S3Target target = settings.s3Target();
        Bundle b = bundle;
        if (b == null || !b.target().equals(target)) {
            synchronized (this) {
                b = bundle;
                if (b == null || !b.target().equals(target)) {
                    b = build(target);
                    bundle = b;
                }
            }
        }
        return b;
    }

    private Bundle build(StorageSettings.S3Target target) {
        log.info("构建 RustFS 客户端: endpoint={}, bucket={}", target.endpoint(), target.bucket());
        S3Client s3 = S3Client.builder()
                .endpointOverride(URI.create(target.endpoint()))
                // RustFS 文档固定要求 us-east-1，配合 path-style 避免 301 Moved Permanently
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        target.accessKey(), target.secretKey())))
                .forcePathStyle(true)
                .build();
        S3Presigner presigner = S3Presigner.builder()
                .endpointOverride(URI.create(target.endpoint()))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        target.accessKey(), target.secretKey())))
                // 预签名 URL 的 path-style 需单独开启（与 S3Client 配置对称，见 RustFS 文档）
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
        provision(s3, target);
        Bundle built = new Bundle(target, s3, presigner);
        Bundle old = bundle;
        if (old != null) {
            try {
                old.s3().close();
                old.presigner().close();
            } catch (RuntimeException e) {
                log.debug("关闭旧 RustFS 客户端失败: {}", e.toString());
            }
        }
        return built;
    }

    /** 桶预配：建桶、tmp/ 生命周期、公开读策略，全部幂等且失败仅告警（不影响上传主流程） */
    private void provision(S3Client s3, StorageSettings.S3Target target) {
        ensureBucket(s3, target.bucket());
        ensureTmpLifecycle(s3, target);
        if (target.publicRead()) {
            ensurePublicReadPolicy(s3, target.bucket());
        }
    }

    @Override
    public String save(String relativePath, String contentType, InputStream in, long size) {
        Bundle b = bundle();
        try {
            b.s3().putObject(PutObjectRequest.builder()
                            .bucket(b.target().bucket())
                            .key(relativePath)
                            // 预签名下载时 RustFS 按存储的 Content-Type 响应，内联展示全靠它
                            .contentType(contentType == null || contentType.isBlank()
                                    ? "application/octet-stream" : contentType)
                            // 对象级缓存策略随对象存储，公开读时浏览器/CDN 按此缓存
                            .cacheControl(StorageService.cacheControlFor(relativePath))
                            .build(),
                    // SDK 传输完成后自动关闭输入流
                    RequestBody.fromInputStream(in, size));
        } catch (RuntimeException e) {
            log.error("RustFS 上传失败: {}", relativePath, e);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "文件保存失败");
        }
        return StorageService.urlOf(relativePath);
    }

    @Override
    public void delete(String relativePath) {
        Bundle b = bundle();
        try {
            b.s3().deleteObject(DeleteObjectRequest.builder()
                    .bucket(b.target().bucket()).key(relativePath).build());
        } catch (RuntimeException e) {
            log.warn("RustFS 删除对象失败: {}", relativePath);
        }
    }

    @Override
    public Duration presignTtl() {
        return settings.presignTtl();
    }

    @Override
    public String presignPutUrl(String key, String contentType, Duration ttl) {
        Bundle b = bundle();
        var presigned = b.presigner().presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .putObjectRequest(PutObjectRequest.builder()
                        .bucket(b.target().bucket())
                        .key(key)
                        // Content-Type 参与签名：前端 PUT 必须原样携带该请求头
                        .contentType(contentType)
                        .build())
                .build());
        return presigned.url().toString();
    }

    @Override
    public URI presignGetUrl(String key, Duration ttl) {
        Bundle b = bundle();
        var presigned = b.presigner().presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(b.target().bucket()).key(key).build())
                .build());
        return URI.create(presigned.url().toString());
    }

    @Override
    public Stat stat(String key) {
        Bundle b = bundle();
        try {
            var head = b.s3().headObject(HeadObjectRequest.builder()
                    .bucket(b.target().bucket()).key(key).build());
            return new Stat(head.contentType(), head.contentLength());
        } catch (NoSuchKeyException e) {
            return null;
        }
    }

    @Override
    public void copy(String fromKey, String toKey, String contentType) {
        Bundle b = bundle();
        try {
            // 直传的 tmp/ 对象由前端 PUT，元数据不可信：REPLACE 覆写为白名单类型 + 目标前缀的缓存策略
            b.s3().copyObject(CopyObjectRequest.builder()
                    .sourceBucket(b.target().bucket()).sourceKey(fromKey)
                    .destinationBucket(b.target().bucket()).destinationKey(toKey)
                    .metadataDirective(MetadataDirective.REPLACE)
                    .contentType(contentType)
                    .cacheControl(StorageService.cacheControlFor(toKey))
                    .build());
        } catch (RuntimeException e) {
            log.error("RustFS 复制对象失败: {} -> {}", fromKey, toKey, e);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "文件处理失败");
        }
    }

    /** 桶不存在则创建；已存在或无建桶权限仅提示，真正上传时再暴露问题 */
    private void ensureBucket(S3Client s3, String bucket) {
        try {
            s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            log.info("RustFS bucket 已创建: {}", bucket);
        } catch (BucketAlreadyOwnedByYouException | BucketAlreadyExistsException e) {
            log.debug("RustFS bucket 已存在: {}", bucket);
        } catch (RuntimeException e) {
            log.warn("RustFS bucket 自动创建失败（若已在控制台创建或对象存储暂不可达可忽略）: {}",
                    e.toString());
        }
    }

    /** 幂等安装 tmp/ 直传暂存的生命周期过期规则，未发布的直传文件到点自动回收 */
    private void ensureTmpLifecycle(S3Client s3, StorageSettings.S3Target target) {
        try {
            var rules = new ArrayList<LifecycleRule>();
            try {
                rules.addAll(s3.getBucketLifecycleConfiguration(b -> b.bucket(target.bucket())).rules());
            } catch (S3Exception e) {
                // 2.25.x 无专用异常类：桶上还没有生命周期规则时 RustFS 回 404/NoSuchLifecycleConfiguration
                if (e.statusCode() != 404
                        && (e.awsErrorDetails() == null
                        || !"NoSuchLifecycleConfiguration".equals(e.awsErrorDetails().errorCode()))) {
                    throw e;
                }
            }
            if (rules.stream().anyMatch(r -> TMP_LIFECYCLE_RULE_ID.equals(r.id()))) {
                return;
            }
            rules.add(LifecycleRule.builder()
                    .id(TMP_LIFECYCLE_RULE_ID)
                    .filter(LifecycleRuleFilter.builder().prefix(TMP_PREFIX).build())
                    .status(ExpirationStatus.ENABLED)
                    .expiration(LifecycleExpiration.builder()
                            .days(target.tmpExpireDays()).build())
                    .build());
            s3.putBucketLifecycleConfiguration(PutBucketLifecycleConfigurationRequest.builder()
                    .bucket(target.bucket())
                    .lifecycleConfiguration(BucketLifecycleConfiguration.builder().rules(rules).build())
                    .build());
            log.info("RustFS {} 直传过期规则已就绪: {} 天", TMP_PREFIX, target.tmpExpireDays());
        } catch (RuntimeException e) {
            // 不影响上传主流程，只是 tmp/ 不会被自动清理
            log.warn("RustFS 生命周期规则配置失败（不影响上传，仅影响 {} 自动清理）: {}",
                    TMP_PREFIX, e.toString());
        }
    }

    /**
     * 公开读：对 moment/ 与 avatar/ 前缀安装匿名只读策略（tmp/ 与列举保持私有），
     * 下载跳固定直链后浏览器/CDN 缓存才能长期命中。幂等：策略与期望一致时不重写。
     */
    private void ensurePublicReadPolicy(S3Client s3, String bucket) {
        String desired = """
                {"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":"*",\
                "Action":["s3:GetObject"],\
                "Resource":["arn:aws:s3:::%s/moment/*","arn:aws:s3:::%s/avatar/*"]}]}"""
                .formatted(bucket, bucket);
        try {
            String current = s3.getBucketPolicy(b -> b.bucket(bucket)).policy();
            if (desired.equals(current)) {
                return;
            }
            s3.putBucketPolicy(PutBucketPolicyRequest.builder()
                    .bucket(bucket).policy(desired).build());
            log.info("RustFS 公开读桶策略已安装（匿名只读 moment/ 与 avatar/，tmp/ 与列举仍私有）");
        } catch (S3Exception e) {
            if (e.statusCode() == 404
                    || (e.awsErrorDetails() != null && "NoSuchBucketPolicy".equals(e.awsErrorDetails().errorCode()))) {
                s3.putBucketPolicy(PutBucketPolicyRequest.builder()
                        .bucket(bucket).policy(desired).build());
                log.info("RustFS 公开读桶策略已安装（匿名只读 moment/ 与 avatar/，tmp/ 与列举仍私有）");
                return;
            }
            log.warn("RustFS 公开读桶策略安装失败（下载将退回预签名模式）: {}", e.toString());
        } catch (RuntimeException e) {
            log.warn("RustFS 公开读桶策略安装失败（下载将退回预签名模式）: {}", e.toString());
        }
    }
}

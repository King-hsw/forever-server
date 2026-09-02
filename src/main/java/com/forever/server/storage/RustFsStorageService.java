package com.forever.server.storage;

import com.forever.server.common.BizException;
import com.forever.server.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.List;

/**
 * 文件存取（S3 兼容对象存储 RustFS，AWS SDK v2 接入，见 docs.rustfs.com/zh/developer/sdk/java）。
 * 相对路径即对象 key；前端与数据库一律使用 RustFS 直链（{endpoint}/{bucket}/{key}），
 * 由 {@link #directUrlOf} 按当前配置即时拼出（桶需已在 RustFS 控制台设为匿名可读）。
 * path-style + us-east-1 是 RustFS 的固定要求。S3 客户端按 {@link StorageSettings} 的
 * 当前目标元组惰性构建——后台修改存储配置（endpoint/密钥/桶/直传有效期）后，
 * 下次使用自动重建，无需重启。
 * 应用只读写对象，不碰桶：建桶与桶策略（公开/私有）由运营在 RustFS 控制台处置。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RustFsStorageService {

    /**
     * 分片摘要：listParts 返回、completeMultipart 消费
     */
    public record PartDigest(int partNumber, long size, String etag) {
    }

    /**
     * 对象元数据快照（转正时校验大小/类型用）
     */
    public record Stat(String contentType, long size) {
    }

    private final StorageSettings settings;

    /**
     * S3 客户端 + 预签名器 + 构建时所依据的目标元组（元组变化即重建）
     */
    private record Bundle(StorageSettings.S3Target target, S3Client s3, S3Presigner presigner) {
    }

    private volatile Bundle bundle;

    /**
     * 获取与当前配置一致的客户端；双检锁避免每次调用都进入同步块
     */
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

    /**
     * 保存文件。relativePath 即对象 key，返回其直链。输入流由本类负责关闭
     */
    public String save(String relativePath, String contentType, InputStream in, long size) {
        Bundle b = bundle();
        try {
            b.s3().putObject(PutObjectRequest.builder()
                            .bucket(b.target().bucket())
                            .key(relativePath)
                            // 预签名下载时 RustFS 按存储的 Content-Type 响应，内联展示全靠它
                            .contentType(contentType == null || contentType.isBlank()
                                    ? "application/octet-stream" : contentType)
                            .build(),
                    // SDK 传输完成后自动关闭输入流
                    RequestBody.fromInputStream(in, size));
        } catch (RuntimeException e) {
            log.error("RustFS 上传失败: {}", relativePath, e);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "文件保存失败");
        }
        return directUrlOf(relativePath);
    }

    /**
     * 删除已保存文件；对象不存在时静默，失败仅告警不抛出
     */
    public void delete(String relativePath) {
        Bundle b = bundle();
        try {
            b.s3().deleteObject(DeleteObjectRequest.builder()
                    .bucket(b.target().bucket()).key(relativePath).build());
        } catch (RuntimeException e) {
            log.warn("RustFS 删除对象失败: {}", relativePath);
        }
    }

    /**
     * 预签名 URL 有效期（直传 PUT 与分片共用）
     */
    public Duration presignTtl() {
        return settings.presignTtl();
    }

    /**
     * 签发限时直传 PUT URL，前端携带与 contentType 一致的 Content-Type 头裸 PUT 文件体
     */
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

    /**
     * 对象公网直链：{endpoint}/{bucket}/{key}（按当前配置即时拼出）
     */
    public String directUrlOf(String key) {
        StorageSettings.S3Target target = settings.s3Target();
        return target.endpoint().replaceAll("/+$", "") + "/" + target.bucket() + "/" + key;
    }

    /**
     * 对象元数据；对象不存在返回 null
     */
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

    /**
     * 初始化分片上传，返回 uploadId；Content-Type 在此确定并保留到最终对象
     */
    public String createMultipart(String key, String contentType) {
        Bundle b = bundle();
        try {
            return b.s3().createMultipartUpload(CreateMultipartUploadRequest.builder()
                            .bucket(b.target().bucket()).key(key)
                            .contentType(contentType == null || contentType.isBlank()
                                    ? "application/octet-stream" : contentType)
                            .build())
                    .uploadId();
        } catch (RuntimeException e) {
            log.error("RustFS 初始化分片上传失败: {}", key, e);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "文件保存失败");
        }
    }

    /**
     * 已上传分片清单（分片号/大小/ETag），断点续传对账与 complete 组装都用它
     */
    public List<PartDigest> listParts(String key, String uploadId) {
        Bundle b = bundle();
        try {
            return b.s3().listParts(ListPartsRequest.builder()
                            .bucket(b.target().bucket()).key(key).uploadId(uploadId).build())
                    .parts().stream()
                    .map(p -> new PartDigest(p.partNumber(), p.size(), p.eTag()))
                    .toList();
        } catch (NoSuchUploadException e) {
            // uploadId 已失效（abort 过或不存在）：对账方据此判定会话死亡
            return null;
        }
    }

    /**
     * 按清单收尾，分片合并为正式对象
     */
    public void completeMultipart(String key, String uploadId, List<PartDigest> parts) {
        Bundle b = bundle();
        try {
            b.s3().completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                    .bucket(b.target().bucket()).key(key).uploadId(uploadId)
                    .multipartUpload(CompletedMultipartUpload.builder()
                            .parts(parts.stream()
                                    .map(p -> CompletedPart.builder()
                                            .partNumber(p.partNumber()).eTag(p.etag()).build())
                                    .toList())
                            .build())
                    .build());
        } catch (RuntimeException e) {
            log.error("RustFS 分片收尾失败: {}", key, e);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "文件处理失败");
        }
    }

    /**
     * 预签名单个分片的直传 PUT URL
     */
    public String presignUploadPartUrl(String key, String uploadId, int partNumber, Duration ttl) {
        Bundle b = bundle();
        var presigned = b.presigner().presignUploadPart(UploadPartPresignRequest.builder()
                .signatureDuration(ttl)
                .uploadPartRequest(UploadPartRequest.builder()
                        .bucket(b.target().bucket()).key(key).uploadId(uploadId).partNumber(partNumber)
                        .build())
                .build());
        return presigned.url().toString();
    }
}

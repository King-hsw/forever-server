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
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ExpirationStatus;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListPartsRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.NoSuchUploadException;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.List;

/**
 * RustFS 存储（S3 兼容，AWS SDK v2 接入，见 docs.rustfs.com/zh/developer/sdk/java）：
 * path-style + us-east-1 是 RustFS 的固定要求。S3 客户端按 {@link StorageSettings} 的
 * 当前目标元组惰性构建——后台修改存储配置（endpoint/密钥/桶/直传有效期）后，
 * 下次使用自动重建，无需重启。
 * 应用只读写对象，不碰桶：建桶与桶策略（公开/私有）由运营在 RustFS 控制台处置，
 * 桶需已存在且允许匿名读，直链（directUrlOf 按当前配置拼出）才能被前端直接访问。
 */
@Slf4j
@Service
public class RustFsStorageService implements StorageService {

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
                            .build(),
                    // SDK 传输完成后自动关闭输入流
                    RequestBody.fromInputStream(in, size));
        } catch (RuntimeException e) {
            log.error("RustFS 上传失败: {}", relativePath, e);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "文件保存失败");
        }
        return directUrlOf(relativePath);
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
    public String directUrlOf(String key) {
        StorageSettings.S3Target target = settings.s3Target();
        return target.endpoint().replaceAll("/+$", "") + "/" + target.bucket() + "/" + key;
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

    @Override
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

    @Override
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

    @Override
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

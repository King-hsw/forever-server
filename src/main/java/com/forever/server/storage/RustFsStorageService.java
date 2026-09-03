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
 * 相对路径即对象 key；前端与数据库一律使用 RustFS 直链（{publicBaseUrl}/{bucket}/{key}，
 * publicBaseUrl 留空回退 endpoint），由 {@link #directUrlOf} 拼出。
 * path-style + us-east-1 是 RustFS 的固定要求。配置在 yml（见 {@link StorageProperties}），
 * 客户端随 Bean 一次性构建。应用只读写对象，不碰桶：建桶与桶策略（公开/私有）由运营在 RustFS 控制台处置。
 */
@Slf4j
@Service
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

    private final StorageProperties props;
    private final S3Client s3;
    private final S3Presigner presigner;

    public RustFsStorageService(StorageProperties props) {
        // 配置缺项/格式错直接启动失败（fail fast）
        props.validate();
        this.props = props;
        log.info("构建 RustFS 客户端: endpoint={}, bucket={}", props.endpoint(), props.bucket());
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(props.accessKey(), props.secretKey()));
        this.s3 = S3Client.builder()
                .endpointOverride(URI.create(props.endpoint()))
                // RustFS 文档固定要求 us-east-1，配合 path-style 避免 301 Moved Permanently
                .region(Region.US_EAST_1)
                .credentialsProvider(credentials)
                .forcePathStyle(true)
                .build();
        this.presigner = S3Presigner.builder()
                .endpointOverride(URI.create(props.endpoint()))
                .region(Region.US_EAST_1)
                .credentialsProvider(credentials)
                // 预签名 URL 的 path-style 需单独开启（与 S3Client 配置对称，见 RustFS 文档）
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    /**
     * 保存文件。relativePath 即对象 key，返回其直链。输入流由本类负责关闭
     */
    public String save(String relativePath, String contentType, InputStream in, long size) {
        try {
            s3.putObject(PutObjectRequest.builder()
                            .bucket(props.bucket())
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
        try {
            s3.deleteObject(DeleteObjectRequest.builder()
                    .bucket(props.bucket()).key(relativePath).build());
        } catch (RuntimeException e) {
            log.warn("RustFS 删除对象失败: {}", relativePath);
        }
    }

    /**
     * 预签名 URL 有效期（直传 PUT 与分片共用）
     */
    public Duration presignTtl() {
        return props.presignTtl();
    }

    /**
     * 签发限时直传 PUT URL，前端携带与 contentType 一致的 Content-Type 头裸 PUT 文件体
     */
    public String presignPutUrl(String key, String contentType, Duration ttl) {
        var presigned = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .putObjectRequest(PutObjectRequest.builder()
                        .bucket(props.bucket())
                        .key(key)
                        // Content-Type 参与签名：前端 PUT 必须原样携带该请求头
                        .contentType(contentType)
                        .build())
                .build());
        return presigned.url().toString();
    }

    /**
     * 对象公网直链：{publicBaseUrl}/{bucket}/{key}（publicBaseUrl 留空回退 endpoint）
     */
    public String directUrlOf(String key) {
        String base = props.publicBaseUrl() == null || props.publicBaseUrl().isBlank()
                ? props.endpoint() : props.publicBaseUrl();
        return base.replaceAll("/+$", "") + "/" + props.bucket() + "/" + key;
    }

    /**
     * 对象元数据；对象不存在返回 null
     */
    public Stat stat(String key) {
        try {
            var head = s3.headObject(HeadObjectRequest.builder()
                    .bucket(props.bucket()).key(key).build());
            return new Stat(head.contentType(), head.contentLength());
        } catch (NoSuchKeyException e) {
            return null;
        }
    }

    /**
     * 初始化分片上传，返回 uploadId；Content-Type 在此确定并保留到最终对象
     */
    public String createMultipart(String key, String contentType) {
        try {
            return s3.createMultipartUpload(CreateMultipartUploadRequest.builder()
                            .bucket(props.bucket())
                            .key(key)
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
        try {
            return s3.listParts(ListPartsRequest.builder()
                            .bucket(props.bucket()).key(key).uploadId(uploadId).build())
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
        try {
            s3.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                    .bucket(props.bucket()).key(key).uploadId(uploadId)
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
        var presigned = presigner.presignUploadPart(UploadPartPresignRequest.builder()
                .signatureDuration(ttl)
                .uploadPartRequest(UploadPartRequest.builder()
                        .bucket(props.bucket())
                        .key(key)
                        .uploadId(uploadId)
                        .partNumber(partNumber)
                        .build())
                .build());
        return presigned.url().toString();
    }
}

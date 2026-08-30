package com.forever.server.storage;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;

/**
 * 文件存取抽象（唯一实现：{@link RustFsStorageService}，S3 兼容对象存储）。
 * 生效配置由 {@link StorageSettings} 解析——站点设置（storage.*）优先，yml/环境变量兜底，
 * 后台修改立即生效无需重启。相对路径即对象 key（如 moment/2026/08/{uuid}.jpg）；
 * 公开桶，前端与数据库一律使用 RustFS 直链（{endpoint}/{bucket}/{key}），
 * 由 {@link #directUrlOf} 按当前配置即时拼出。
 */
public interface StorageService {

    /** 保存文件。relativePath 形如 avatar/avatar-1.jpg（同路径覆盖写），
     * 返回对象 key 的直链。输入流由实现负责关闭。 */
    String save(String relativePath, String contentType, InputStream in, long size);

    /** 删除已保存文件；对象不存在时静默，失败仅告警不抛出 */
    void delete(String relativePath);

    /** 预签名 URL 有效期（直传 PUT 与分片共用） */
    Duration presignTtl();

    /** 签发限时直传 PUT URL，前端携带与 contentType 一致的 Content-Type 头裸 PUT 文件体 */
    String presignPutUrl(String key, String contentType, Duration ttl);

    /** 对象公网直链：{endpoint}/{bucket}/{key}（公开桶，按当前配置即时拼出） */
    String directUrlOf(String key);

    /** 对象元数据；对象不存在返回 null */
    Stat stat(String key);

    // ---------- 分片直传 ----------

    /** 初始化分片上传，返回 uploadId；Content-Type 在此确定并保留到最终对象 */
    String createMultipart(String key, String contentType);

    /** 已上传分片清单（分片号/大小/ETag），断点续传对账与 complete 组装都用它 */
    List<PartDigest> listParts(String key, String uploadId);

    /** 按清单收尾，分片合并为正式对象 */
    void completeMultipart(String key, String uploadId, List<PartDigest> parts);

    /** 预签名单个分片的直传 PUT URL */
    String presignUploadPartUrl(String key, String uploadId, int partNumber, Duration ttl);

    /** 分片摘要：listParts 返回、completeMultipart 消费 */
    record PartDigest(int partNumber, long size, String etag) {
    }

    /**
     * 按对象 key 前缀给出浏览器缓存策略：
     * moment/ 为 uuid 不可变内容 → 强缓存一年；avatar/ 同 key 覆盖写 → 每次再校验（304 廉价）；
     * 其余不给缓存头。
     */
    static String cacheControlFor(String key) {
        if (key == null) {
            return null;
        }
        if (key.startsWith("moment/")) {
            return "public, max-age=31536000, immutable";
        }
        if (key.startsWith("avatar/")) {
            return "no-cache";
        }
        return null;
    }

    /** 对象元数据快照（转正时校验大小/类型用） */
    record Stat(String contentType, long size) {
    }
}

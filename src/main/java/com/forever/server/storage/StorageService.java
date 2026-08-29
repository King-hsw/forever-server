package com.forever.server.storage;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;

/**
 * 文件存取抽象（唯一实现：{@link RustFsStorageService}，S3 兼容对象存储）。
 * 生效配置由 {@link StorageSettings} 解析——站点设置（storage.*）优先，yml/环境变量兜底，
 * 后台修改立即生效无需重启。相对路径即对象 key（如 moment/2026/08/{uuid}.jpg），
 * 数据库存的访问地址恒为 /uploads/{相对路径}，由 UploadsController 302 到实际对象。
 */
public interface StorageService {

    /** 入库访问地址前缀，与 UploadsController 对应 */
    String URL_PREFIX = "/uploads/";

    /** 直传暂存前缀：前端直传落这里，发布时收口到正式前缀，生命周期规则定期清理 */
    String TMP_PREFIX = "tmp/";

    /** 保存文件。relativePath 形如 avatar/avatar-1.jpg（同路径覆盖写），
     * 返回入库 URL（恒为 /uploads/{relativePath}）。输入流由实现负责关闭。 */
    String save(String relativePath, String contentType, InputStream in, long size);

    /** 删除已保存文件；对象不存在时静默，失败仅告警不抛出 */
    void delete(String relativePath);

    /** 预签名 URL 有效期（下载 302 与直传 PUT 共用） */
    Duration presignTtl();

    /** 签发限时直传 PUT URL，前端携带与 contentType 一致的 Content-Type 头裸 PUT 文件体 */
    String presignPutUrl(String key, String contentType, Duration ttl);

    /** 签发限时下载 GET URL（/uploads 访问 302 用） */
    URI presignGetUrl(String key, Duration ttl);

    /** 对象元数据；对象不存在返回 null */
    Stat stat(String key);

    /** 同桶内复制对象并覆写元数据（直传收口：tmp/ -> moment/），contentType 落为新对象 Content-Type */
    void copy(String fromKey, String toKey, String contentType);

    /**
     * 按对象 key 前缀给出浏览器缓存策略：
     * moment/ 为 uuid 不可变内容 → 强缓存一年；avatar/ 同 key 覆盖写 → 每次再校验（304 廉价）；
     * 其余（tmp/ 等）不给缓存头。
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

    /** 对象元数据快照（直传收口时校验大小/类型用） */
    record Stat(String contentType, long size) {
    }

    static String urlOf(String relativePath) {
        return URL_PREFIX + relativePath;
    }

    /** /uploads/xxx -> xxx；非本站地址返回 null */
    static String relativeOf(String url) {
        if (url == null || !url.startsWith(URL_PREFIX)) {
            return null;
        }
        return url.substring(URL_PREFIX.length());
    }
}

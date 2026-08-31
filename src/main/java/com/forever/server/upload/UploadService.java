package com.forever.server.upload;

import com.forever.server.common.BizException;
import com.forever.server.common.ErrorCode;
import com.forever.server.storage.RustFsStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 统一上传服务（无状态）：内容寻址 + 预签名直传 + 分片收尾。
 * key = {md5}.{ext}（桶根直存，内容寻址）——同内容天然同 key，
 * 秒传 = 对该地址一次 stat；分片会话状态由 RustFS 自持（uploadId/listParts），
 * 数据库零参与；垃圾对象由运营侧处置。
 */
@Slf4j
@Service
public class UploadService {

    /**
     * 分片大小 8MB：满足 S3 规范"非末尾分片 ≥5MB"，前端无需感知
     */
    static final long PART_SIZE = 8L * 1024 * 1024;

    /**
     * S3 分片数上限
     */
    private static final int MAX_PART_COUNT = 10000;

    /**
     * md5 十六进制小写 32 位；同时保证 key 路径安全（不含目录分隔等字符）
     */
    private static final Pattern MD5 = Pattern.compile("^[0-9a-f]{32}$");

    /**
     * 媒体白名单：Content-Type -> 扩展名
     */
    private static final Map<String, String> EXT_BY_CONTENT_TYPE = Map.ofEntries(
            Map.entry("image/jpeg", ".jpg"),
            Map.entry("image/png", ".png"),
            Map.entry("image/webp", ".webp"),
            Map.entry("image/gif", ".gif"),
            Map.entry("audio/mpeg", ".mp3"),
            Map.entry("audio/mp4", ".m4a"),
            Map.entry("audio/wav", ".wav"),
            Map.entry("video/mp4", ".mp4"),
            Map.entry("video/webm", ".webm"),
            Map.entry("video/matroska", ".mkv"));

    private static final long IMAGE_MAX_BYTES = 100L * 1024 * 1024 * 1024;
    private static final long AUDIO_MAX_BYTES = 100L * 1024 * 1024 * 1024;
    private static final long VIDEO_MAX_BYTES = 100L * 1024 * 1024 * 1024;

    static final String WHITELIST_HINT =
            "仅支持 jpg / png / webp / gif 图片、mp3 / m4a / wav 音频、mp4 / webm / mkv 视频";

    private final RustFsStorageService storage;

    public UploadService(RustFsStorageService storage) {
        this.storage = storage;
    }

    // ---------- 秒传查询 ----------

    /**
     * 秒传查询：只查不签发。公开桶已有同内容对象则返回其直链，前端直接使用。
     */
    public UploadDtos.CheckResponse check(String contentTypeRaw, String md5) {
        String contentType = normalizeContentType(contentTypeRaw);
        String key = requireMd5(md5) + requireWhitelisted(contentType);
        RustFsStorageService.Stat stat = storage.stat(key);
        boolean exists = stat != null;
        log.info("upload checked: key={}, exists={}", key, exists);
        return new UploadDtos.CheckResponse(exists,
                exists ? storage.directUrlOf(key) : null, contentType);
    }

    // ---------- 单文件直传 ----------

    public UploadDtos.PresignResponse presign(String contentTypeRaw, String md5) {
        String contentType = normalizeContentType(contentTypeRaw);
        String ext = requireWhitelisted(contentType);
        String key = requireMd5(md5) + ext;
        String uploadUrl = storage.presignPutUrl(key, contentType, storage.presignTtl());
        return new UploadDtos.PresignResponse(key, uploadUrl,
                storage.directUrlOf(key), contentType, storage.presignTtl().toSeconds());
    }

    // ---------- 分片直传 ----------

    public UploadDtos.MultipartInitResponse multipartInit(String contentTypeRaw,
                                                          String md5, long sizeBytes) {
        String contentType = normalizeContentType(contentTypeRaw);
        String ext = requireWhitelisted(contentType);
        String key = requireMd5(md5) + ext;
        if (sizeBytes <= 0 || sizeBytes > maxBytesFor(contentType)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "文件大小超出限制");
        }
        int partCount = (int) ((sizeBytes + PART_SIZE - 1) / PART_SIZE);
        Duration ttl = storage.presignTtl();

        // 秒传：同内容对象已存在（哪怕之前是分片合并的），直接返回直链
        if (storage.stat(key) != null) {
            log.info("multipart deduplicated: key={}", key);
            return new UploadDtos.MultipartInitResponse(key, null,
                    PART_SIZE, 0, List.of(), storage.directUrlOf(key), contentType, ttl.toSeconds());
        }

        String uploadId = storage.createMultipart(key, contentType);
        log.info("multipart inited: key={}, partCount={}", key, partCount);
        return new UploadDtos.MultipartInitResponse(key, uploadId, PART_SIZE, partCount,
                presignParts(key, uploadId, partCount), storage.directUrlOf(key), contentType,
                ttl.toSeconds());
    }

    /**
     * 收尾：核对分片（连续、非末片=8MB、总量≤上限）后合并为正式对象
     */
    public UploadDtos.MultipartCompleteResponse complete(String key, String uploadId) {
        requireValidKey(key);
        String contentType = contentTypeOfKey(key);
        List<RustFsStorageService.PartDigest> parts = new ArrayList<>(listAliveParts(key, uploadId));
        parts.sort(Comparator.comparingInt(RustFsStorageService.PartDigest::partNumber));

        // 无状态校验：非末片必须等于固定 8MB（S3 也强制 ≥5MB），末片 ≤8MB 且 >0，总量 ≤ 上限
        long total = 0;
        for (int i = 0; i < parts.size(); i++) {
            RustFsStorageService.PartDigest part = parts.get(i);
            if (part.partNumber() != i + 1) {
                throw new BizException(ErrorCode.BAD_REQUEST, "分片序号不连续，请重新上传");
            }
            boolean isLast = i == parts.size() - 1;
            if (!isLast && part.size() != PART_SIZE) {
                throw new BizException(ErrorCode.BAD_REQUEST, "分片 " + part.partNumber() + " 大小不符，请重传该分片");
            }
            if (isLast && (part.size() <= 0 || part.size() > PART_SIZE)) {
                throw new BizException(ErrorCode.BAD_REQUEST, "末片大小不符，请重传该分片");
            }
            total += part.size();
        }
        if (parts.size() > MAX_PART_COUNT || total > maxBytesFor(contentType)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "文件大小超出限制");
        }
        storage.completeMultipart(key, uploadId, parts);
        log.info("multipart completed: key={}, total={}B", key, total);
        return new UploadDtos.MultipartCompleteResponse(key, storage.directUrlOf(key), total);
    }

    // ---------- internal ----------

    private List<String> presignParts(String key, String uploadId, int partCount) {
        Duration ttl = storage.presignTtl();
        List<String> urls = new ArrayList<>(partCount);
        for (int i = 1; i <= partCount; i++) {
            urls.add(storage.presignUploadPartUrl(key, uploadId, i, ttl));
        }
        return urls;
    }

    /**
     * 分片对账；uploadId 已失效（不存在/已被覆盖完成）返回业务异常，前端重新走 init
     */
    private List<RustFsStorageService.PartDigest> listAliveParts(String key, String uploadId) {
        if (key == null || key.isBlank() || uploadId == null || uploadId.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "缺少 key 或 uploadId");
        }
        List<RustFsStorageService.PartDigest> parts = storage.listParts(key, uploadId);
        if (parts == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "分片会话已失效，请重新上传");
        }
        return parts;
    }

    /**
     * key 必须形如 {32位md5}.{白名单扩展名}——内容寻址的固定形状，杜绝路径注入
     */
    private void requireValidKey(String key) {
        if (key == null || key.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "缺少对象 key");
        }
        String contentType = contentTypeOfKey(key);
        if (contentType == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "非法的对象 key：" + key);
        }
        String body = key.substring(0, key.lastIndexOf('.'));
        if (!MD5.matcher(body).matches()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "非法的对象 key：" + key);
        }
    }

    /**
     * 由 key 的扩展名反查 Content-Type；不在白名单返回 null
     */
    private String contentTypeOfKey(String key) {
        int dot = key.lastIndexOf('.');
        if (dot < 0) {
            return null;
        }
        String ext = key.substring(dot).toLowerCase();
        return EXT_BY_CONTENT_TYPE.entrySet().stream()
                .filter(e -> e.getValue().equals(ext))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private String requireWhitelisted(String contentType) {
        String ext = EXT_BY_CONTENT_TYPE.get(contentType);
        if (ext == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, WHITELIST_HINT);
        }
        return ext;
    }

    /**
     * md5 归一化并强校验（32 位十六进制小写）；它是 key 的一部分，必须杜绝路径注入
     */
    private static String requireMd5(String md5) {
        String hash = md5 == null ? "" : md5.trim().toLowerCase();
        if (!MD5.matcher(hash).matches()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "md5 不合法（32 位十六进制）");
        }
        return hash;
    }

    /**
     * 该 Content-Type 的单文件大小上限
     */
    private static long maxBytesFor(String contentType) {
        if (contentType.startsWith("image/")) {
            return IMAGE_MAX_BYTES;
        }
        if (contentType.startsWith("audio/")) {
            return AUDIO_MAX_BYTES;
        }
        return VIDEO_MAX_BYTES;
    }

    /**
     * MIME 归一化：去参数、转小写，如 "image/png; charset=x" -> "image/png"
     */
    static String normalizeContentType(String contentType) {
        return contentType == null || contentType.isBlank() ? ""
                : contentType.split(";")[0].trim().toLowerCase();
    }
}

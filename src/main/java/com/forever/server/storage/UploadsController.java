package com.forever.server.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Duration;

/**
 * /uploads/** 统一访问入口：数据库与页面引用的 /uploads/... 稳定地址在此 302 到对象存储——
 * 公开读跳 {endpoint}/{bucket}/{key} 固定直链（浏览器/CDN 缓存可长期命中，跳转本身可短缓存），
 * 私有桶跳预签名下载 URL（storage.presign-ttl，默认 15 分钟）。
 * tmp/ 是直传暂存区，只允许发布流程内部收口，不对外提供读取。
 * /uploads/** 在 SecurityConfig 中 permitAll。
 */
@Slf4j
@RestController
public class UploadsController {

    private final StorageSettings settings;
    private final StorageService storage;

    public UploadsController(StorageSettings settings, StorageService storage) {
        this.settings = settings;
        this.storage = storage;
    }

    @GetMapping("/uploads/{*path}")
    public ResponseEntity<Object> serve(@PathVariable String path) {
        String relative = sanitizeRelative(path);
        if (relative == null || relative.startsWith(StorageService.TMP_PREFIX)) {
            return ResponseEntity.notFound().build();
        }
        // 先做完整性校验：配置残缺时统一返回明确的业务错误而非深处的裸异常
        settings.s3Target();
        // 公开读：302 到固定直链（目标 URL 恒定，浏览器/CDN 缓存长期命中）
        if (settings.publicRead()) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic())
                    .location(stableUrl(relative))
                    .build();
        }
        // 私有桶：预签名 URL 不入库、即取即用，对象不存在时由 RustFS 返回 404
        return ResponseEntity.status(HttpStatus.FOUND)
                .cacheControl(CacheControl.noStore())
                .location(storage.presignGetUrl(relative, settings.presignTtl()))
                .build();
    }

    /** 公开读模式的固定直链：{endpoint}/{bucket}/{key}，无签名参数，缓存友好 */
    private URI stableUrl(String relative) {
        return URI.create(settings.endpoint().replaceAll("/+$", "")
                + "/" + settings.bucket() + "/" + relative);
    }

    /** {*path} 捕获值去头并防目录穿越；非法返回 null（404） */
    static String sanitizeRelative(String capturedPath) {
        String relative = capturedPath == null ? "" : capturedPath.strip();
        if (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        if (relative.isBlank() || relative.startsWith("/") || relative.contains("..")) {
            return null;
        }
        return relative;
    }
}

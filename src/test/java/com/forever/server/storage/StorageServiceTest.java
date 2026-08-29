package com.forever.server.storage;

import com.forever.server.common.BizException;
import com.forever.server.setting.SiteConfig;
import com.forever.server.setting.SiteConfigMapper;
import com.forever.server.setting.SiteConfigService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 存储模块自检：设置解析（站点设置优先/yml 兜底/保存校验）与 /uploads 路径清洗、302 分流 */
class StorageServiceTest {

    /** 内存版站点配置 Mapper，不依赖数据库 */
    private static SiteConfigService configService(Map<String, String> seed) {
        Map<String, String> store = new HashMap<>(seed);
        return new SiteConfigService(new SiteConfigMapper() {
            @Override
            public List<SiteConfig> findAll() {
                return store.entrySet().stream().map(e -> {
                    var c = new SiteConfig();
                    c.setConfigKey(e.getKey());
                    c.setConfigValue(e.getValue());
                    return c;
                }).toList();
            }

            @Override
            public int upsert(String key, String value) {
                store.put(key, value);
                return 1;
            }
        });
    }

    @Test
    void 站点设置优先于yml且修改即时生效() {
        var service = configService(Map.of());
        var props = new StorageProperties("http://yml:9000", "ak-yml", "sk-yml", "yml-bucket",
                null, null, null);
        var settings = new StorageSettings(service, props);
        assertEquals("http://yml:9000", settings.endpoint());
        assertEquals("ak-yml", settings.accessKey());
        assertEquals("yml-bucket", settings.bucket());
        assertEquals(Duration.ofMinutes(15), settings.presignTtl());
        assertEquals(1, settings.tmpExpireDays());
        // 后台保存后立即生效，无需重启
        service.update(SiteConfigService.STORAGE_ENDPOINT, "http://db:9000");
        service.update(SiteConfigService.STORAGE_PRESIGN_TTL, "30m");
        service.update(SiteConfigService.STORAGE_TMP_EXPIRE_DAYS, "3");
        service.update(SiteConfigService.STORAGE_PUBLIC_READ, "true");
        assertEquals("http://db:9000", settings.endpoint());
        assertEquals(Duration.ofMinutes(30), settings.presignTtl());
        assertEquals(3, settings.tmpExpireDays());
        assertTrue(settings.publicRead());
    }

    @Test
    void 连接信息缺失时使用时报错补齐后恢复() {
        var service = configService(Map.of());
        // accessKey/secretKey 走 yml 兜底，endpoint/bucket 待补
        var settings = new StorageSettings(service,
                new StorageProperties(null, "ak", "sk", null, null, null, null));
        var e = assertThrows(BizException.class, settings::s3Target);
        assertTrue(e.getMessage().contains("配置不完整"));
        service.update(SiteConfigService.STORAGE_ENDPOINT, "http://127.0.0.1:9000");
        service.update(SiteConfigService.STORAGE_BUCKET, "forever");
        var target = settings.s3Target();
        assertEquals("http://127.0.0.1:9000", target.endpoint());
        assertEquals("forever", target.bucket());
        assertEquals("ak", target.accessKey());
        assertEquals(1, target.tmpExpireDays());
    }

    @Test
    void 站点设置保存校验拒绝非法值() {
        var service = configService(Map.of());
        assertThrows(BizException.class, () -> service.update(SiteConfigService.STORAGE_ENDPOINT, "ftp://x"));
        assertThrows(BizException.class, () -> service.update(SiteConfigService.STORAGE_PRESIGN_TTL, "abc"));
        assertThrows(BizException.class, () -> service.update(SiteConfigService.STORAGE_TMP_EXPIRE_DAYS, "0"));
        assertThrows(BizException.class, () -> service.update(SiteConfigService.STORAGE_TMP_EXPIRE_DAYS, "x"));
        assertThrows(BizException.class, () -> service.update(SiteConfigService.STORAGE_PUBLIC_READ, "yes"));
        // 合法值（ISO-8601 时长）可保存
        service.update(SiteConfigService.STORAGE_PRESIGN_TTL, "PT15M");
        assertEquals("PT15M", service.getString(SiteConfigService.STORAGE_PRESIGN_TTL, null));
    }

    @Test
    void 公开读模式302到固定直链并去末尾斜杠() {
        var service = configService(Map.of(SiteConfigService.STORAGE_PUBLIC_READ, "true"));
        var settings = new StorageSettings(service, new StorageProperties(
                "http://127.0.0.1:9000/", "ak", "sk", "forever", null, null, null));
        var controller = new UploadsController(settings, new RustFsStorageService(settings));
        var response = controller.serve("/moment/2026/08/a.jpg");
        assertEquals(302, response.getStatusCode().value());
        assertEquals("http://127.0.0.1:9000/forever/moment/2026/08/a.jpg",
                String.valueOf(response.getHeaders().getLocation()));
        // 跳转本身可短缓存
        assertTrue(String.valueOf(response.getHeaders().getCacheControl()).contains("max-age=600"));
    }

    @Test
    void 私有桶302到预签名URL() {
        var service = configService(Map.of());
        var settings = new StorageSettings(service, new StorageProperties(
                "http://127.0.0.1:9000", "ak", "sk", "forever", Duration.ofMinutes(10), null, null));
        var controller = new UploadsController(settings, new RustFsStorageService(settings));
        var response = controller.serve("/moment/2026/08/never-exists.jpg");
        assertEquals(302, response.getStatusCode().value());
        String location = String.valueOf(response.getHeaders().getLocation());
        // path-style：桶在路径上；签名与过期参数由 SDK 计算
        assertTrue(location.contains("http://127.0.0.1:9000/forever/moment/2026/08/never-exists.jpg"));
        assertTrue(location.contains("X-Amz-Signature"));
        assertTrue(location.contains("X-Amz-Expires=600"));
    }

    @Test
    void tmp路径与穿越路径直接404() {
        var settings = new StorageSettings(configService(Map.of()), new StorageProperties(
                "http://127.0.0.1:9000", "ak", "sk", "forever", null, null, null));
        var controller = new UploadsController(settings, new RustFsStorageService(settings));
        // tmp/ 是直传暂存区，不对外提供读取
        assertEquals(404, controller.serve("/tmp/2026/08/abc.png").getStatusCode().value());
        assertEquals(404, controller.serve("/../application.yml").getStatusCode().value());
    }

    @Test
    void uploads路径清洗拒绝穿越与空路径() {
        assertEquals("moment/2026/08/a.jpg", UploadsController.sanitizeRelative("/moment/2026/08/a.jpg"));
        assertEquals("a.jpg", UploadsController.sanitizeRelative("a.jpg"));
        assertNull(UploadsController.sanitizeRelative("/"));
        assertNull(UploadsController.sanitizeRelative(""));
        assertNull(UploadsController.sanitizeRelative("/../application.yml"));
        assertNull(UploadsController.sanitizeRelative("/a/../../etc/passwd"));
    }
}

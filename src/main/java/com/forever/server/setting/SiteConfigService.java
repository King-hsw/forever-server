package com.forever.server.setting;

import com.forever.server.common.BizException;
import com.forever.server.common.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 站点动态配置。全量缓存在内存中，读多写少场景；
 * 管理端修改即时生效并落库，重启不丢。
 */
@Slf4j
@Service
public class SiteConfigService {

    /**
     * 同 IP 发表评论的最小间隔（秒），0 表示不限流
     */
    public static final String COMMENT_POST_INTERVAL_SECONDS = "comment.post-interval-seconds";
    /**
     * 新评论是否直接过审（false = 先审后显）
     */
    public static final String COMMENT_AUTO_APPROVE = "comment.auto-approve";
    /**
     * 建站日期（yyyy-MM-dd），前台页脚据此计算运行时长
     */
    public static final String SITE_BIRTH_DATE = "site.birth-date";

    /**
     * 已知配置项元数据：key -> 中文说明（新增可调参数在这里登记）
     */
    private static final Map<String, String> KNOWN_KEYS = Map.ofEntries(
            Map.entry(COMMENT_POST_INTERVAL_SECONDS, "同一 IP 发表评论的最小间隔（秒），0 表示不限流"),
            Map.entry(COMMENT_AUTO_APPROVE, "新评论是否直接过审，false = 先审后显（true/false）"),
            Map.entry(SITE_BIRTH_DATE, "建站日期，格式 yyyy-MM-dd（前台页脚据此计算运行时长）")
    );

    private final SiteConfigMapper mapper;
    /**
     * key -> 当前生效值的内存缓存
     */
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public SiteConfigService(SiteConfigMapper mapper) {
        this.mapper = mapper;
        mapper.findAll().forEach(c -> cache.put(c.getConfigKey(), c.getConfigValue()));
    }

    /**
     * 读取 long 型配置：数据库值优先；未设置或非法时回落 fallback。
     */
    public long getLong(String key, long fallback) {
        String value = cache.get(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            log.warn("site config {}={} is not a number, fallback to {}", key, value, fallback);
            return fallback;
        }
    }

    /**
     * 读取 boolean 配置：未设置或非法时回落 fallback
     */
    public boolean getBoolean(String key, boolean fallback) {
        String value = cache.get(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        if ("true".equalsIgnoreCase(value.trim())) {
            return true;
        }
        if ("false".equalsIgnoreCase(value.trim())) {
            return false;
        }
        log.warn("site config {}={} is not a boolean, fallback to {}", key, value, fallback);
        return fallback;
    }

    /**
     * 读取字符串配置：未设置时回落 fallback（可能为 null）
     */
    public String getString(String key, String fallback) {
        String value = cache.get(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    /**
     * 建站日期；未设置时为 null
     */
    public String birthDate() {
        return getString(SITE_BIRTH_DATE, null);
    }

    public List<SettingDtos.SettingResponse> listAll() {
        return KNOWN_KEYS.entrySet().stream()
                .map(e -> new SettingDtos.SettingResponse(e.getKey(), cache.get(e.getKey()), e.getValue()))
                .toList();
    }

    public SettingDtos.SettingResponse update(String key, String value) {
        if (!KNOWN_KEYS.containsKey(key)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "未知的配置项：" + key);
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            // 留空 = 清除配置、恢复默认值；不做值校验
            mapper.upsert(key, "");
            cache.remove(key);
            log.info("site config cleared: {}", key);
            return new SettingDtos.SettingResponse(key, trimmed, KNOWN_KEYS.get(key));
        }
        if (key.equals(COMMENT_POST_INTERVAL_SECONDS)) {
            try {
                if (Long.parseLong(trimmed) < 0) {
                    throw new BizException(ErrorCode.BAD_REQUEST, "配置值不能为负数");
                }
            } catch (NumberFormatException e) {
                throw new BizException(ErrorCode.BAD_REQUEST, "配置值必须为整数");
            }
        } else if (key.equals(COMMENT_AUTO_APPROVE)) {
            if (!"true".equalsIgnoreCase(trimmed) && !"false".equalsIgnoreCase(trimmed)) {
                throw new BizException(ErrorCode.BAD_REQUEST, "布尔型配置只接受 true/false");
            }
            trimmed = trimmed.toLowerCase();
        } else if (key.equals(SITE_BIRTH_DATE) && !trimmed.isEmpty() && !trimmed.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new BizException(ErrorCode.BAD_REQUEST, "建站日期格式必须为 yyyy-MM-dd");
        }

        mapper.upsert(key, trimmed);
        cache.put(key, trimmed);
        log.info("site config updated: {}={}", key, trimmed);
        return new SettingDtos.SettingResponse(key, trimmed, KNOWN_KEYS.get(key));
    }
}

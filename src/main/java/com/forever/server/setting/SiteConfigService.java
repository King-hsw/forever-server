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

    /** 同 IP 发表评论的最小间隔（秒），0 表示不限流 */
    public static final String COMMENT_POST_INTERVAL_SECONDS = "comment.post-interval-seconds";

    /** 已知配置项元数据：key -> 中文说明（新增可调参数在这里登记） */
    private static final Map<String, String> KNOWN_KEYS = Map.of(
            COMMENT_POST_INTERVAL_SECONDS, "同一 IP 发表评论的最小间隔（秒），0 表示不限流");

    private final SiteConfigMapper mapper;
    /** key -> 当前生效值的内存缓存 */
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public SiteConfigService(SiteConfigMapper mapper) {
        this.mapper = mapper;
        mapper.findAll().forEach(c -> cache.put(c.getConfigKey(), c.getConfigValue()));
    }

    /**
     * 读取 long 型配置：数据库值优先；未设置或非法时回落 fallback
     * （fallback 通常传 application.yml 中的默认值）。
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

    public List<SettingDtos.SettingResponse> listAll() {
        return KNOWN_KEYS.entrySet().stream()
                .map(e -> new SettingDtos.SettingResponse(e.getKey(), cache.get(e.getKey()), e.getValue()))
                .toList();
    }

    public SettingDtos.SettingResponse update(String key, String value) {
        if (!KNOWN_KEYS.containsKey(key)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "未知的配置项：" + key);
        }
        try {
            if (Long.parseLong(value.trim()) < 0) {
                throw new BizException(ErrorCode.BAD_REQUEST, "配置值不能为负数");
            }
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.BAD_REQUEST, "配置值必须为整数");
        }

        mapper.upsert(key, value.trim());
        cache.put(key, value.trim());
        log.info("site config updated: {}={}", key, value.trim());
        return new SettingDtos.SettingResponse(key, value.trim(), KNOWN_KEYS.get(key));
    }
}

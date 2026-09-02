package com.forever.server.setting;

import com.forever.server.common.BizException;
import com.forever.server.common.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
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
     * 是否开启回复邮件通知（需配置 mail.host 等 SMTP 项）
     */
    public static final String COMMENT_NOTIFY_MAIL = "comment.notify-mail";
    /**
     * 新根评论通知站长的邮箱
     */
    public static final String COMMENT_OWNER_EMAIL = "comment.owner-email";
    /**
     * 通知邮件的发件人地址
     */
    public static final String COMMENT_FROM_EMAIL = "comment.from-email";

    // ---------- 邮件（SMTP） ----------

    /**
     * SMTP 服务器地址（必填），如 smtp.qq.com；留空表示未配置邮件
     */
    public static final String MAIL_HOST = "mail.host";
    /**
     * SMTP 端口，默认 465（SSL 直连）
     */
    public static final String MAIL_PORT = "mail.port";
    /**
     * SMTP 登录账号，一般与发件人地址一致
     */
    public static final String MAIL_USERNAME = "mail.username";
    /**
     * SMTP 登录密码或邮箱授权码
     */
    public static final String MAIL_PASSWORD = "mail.password";
    /**
     * 是否 SSL 直连（true = 465 直连，false = 587 STARTTLS），默认 true
     */
    public static final String MAIL_SSL = "mail.ssl";

    /**
     * 站点对外地址，用于拼接文章前台链接与 RSS
     */
    public static final String SITE_URL = "site.url";
    /**
     * 站点名称，用于 RSS 与邮件发件人等对外署名
     */
    public static final String SITE_NAME = "site.name";
    /**
     * 建站日期（yyyy-MM-dd），前台页脚据此计算运行时长
     */
    public static final String SITE_BIRTH_DATE = "site.birth-date";
    /**
     * 留言板标题
     */
    public static final String BOARD_TITLE = "board.title";
    /**
     * 留言板简介
     */
    public static final String BOARD_SUMMARY = "board.summary";
    /**
     * AI 概要总开关；需同时配置 ai.api-key 才真正生效
     */
    public static final String AI_SUMMARY_ENABLED = "ai.summary-enabled";
    /**
     * AI 服务的 API Key（OpenAI 兼容接口）
     */
    public static final String AI_API_KEY = "ai.api-key";
    /**
     * AI 服务地址（OpenAI 兼容接口，如 https://api.deepseek.com）
     */
    public static final String AI_BASE_URL = "ai.base-url";
    /**
     * 模型名（如 gpt-4o-mini / deepseek-chat）
     */
    public static final String AI_MODEL = "ai.model";
    /**
     * 高德 Web 服务 API Key（动态地点逆地理，未配置则逆地理返回 null）
     */
    public static final String MOMENTS_AMAP_KEY = "moments.amapKey";

    // ---------- 文件存储（RustFS 对象存储；未配置的项回落 yml/环境变量，见 StorageSettings） ----------

    /**
     * 对象存储的 S3 API 地址（必填），如 http://127.0.0.1:9000
     */
    public static final String STORAGE_ENDPOINT = "storage.endpoint";
    /**
     * 对象存储访问密钥（必填）
     */
    public static final String STORAGE_ACCESS_KEY = "storage.access-key";
    /**
     * 对象存储秘密密钥（必填）
     */
    public static final String STORAGE_SECRET_KEY = "storage.secret-key";
    /**
     * 存储桶名（必填），缺失时首次使用自动创建
     */
    public static final String STORAGE_BUCKET = "storage.bucket";
    /**
     * 预签名 URL 有效期（直传 PUT 与分片共用），如 15m / PT15M，默认 15m
     */
    public static final String STORAGE_PRESIGN_TTL = "storage.presign-ttl";

    /**
     * 已知配置项元数据：key -> 中文说明（新增可调参数在这里登记）
     */
    private static final Map<String, String> KNOWN_KEYS = Map.ofEntries(
            Map.entry(COMMENT_POST_INTERVAL_SECONDS, "同一 IP 发表评论的最小间隔（秒），0 表示不限流"),
            Map.entry(COMMENT_AUTO_APPROVE, "新评论是否直接过审，false = 先审后显（true/false）"),
            Map.entry(COMMENT_NOTIFY_MAIL, "是否开启评论邮件通知（true/false，需已配置 mail.host 等 SMTP 项）"),
            Map.entry(COMMENT_OWNER_EMAIL, "新根评论通知站长的邮箱"),
            Map.entry(COMMENT_FROM_EMAIL, "通知邮件的发件人地址"),
            Map.entry(MAIL_HOST, "SMTP 服务器地址（必填），如 smtp.qq.com；留空表示未配置邮件"),
            Map.entry(MAIL_PORT, "SMTP 端口，默认 465（SSL 直连）"),
            Map.entry(MAIL_USERNAME, "SMTP 登录账号，一般与发件人地址一致"),
            Map.entry(MAIL_PASSWORD, "SMTP 登录密码或邮箱授权码"),
            Map.entry(MAIL_SSL, "是否 SSL 直连（true = 465 直连，false = 587 STARTTLS，默认 true）"),
            Map.entry(SITE_URL, "站点对外地址，如 https://blog.example.com（用于文章前台链接与 RSS）"),
            Map.entry(SITE_NAME, "站点名称（用于 RSS 标题与邮件发件人等对外署名）"),
            Map.entry(SITE_BIRTH_DATE, "建站日期，格式 yyyy-MM-dd（前台页脚据此计算运行时长）"),
            Map.entry(BOARD_TITLE, "留言板标题"),
            Map.entry(BOARD_SUMMARY, "留言板简介"),
            Map.entry(AI_SUMMARY_ENABLED, "AI 文章概要总开关（true/false），还需配置 ai.api-key 才生效"),
            Map.entry(AI_API_KEY, "AI 服务的 API Key（OpenAI 兼容接口）"),
            Map.entry(AI_BASE_URL, "AI 服务地址（API 根地址，不含 /v1；Spring AI 自动补 /v1/chat/completions；OpenAI 官方即 https://api.openai.com，DeepSeek 即 https://api.deepseek.com）"),
            Map.entry(AI_MODEL, "AI 模型名（如 gpt-4o-mini / deepseek-chat，默认 gpt-4o-mini）"),
            Map.entry(MOMENTS_AMAP_KEY, "高德 Web 服务 API Key（动态地点逆地理，未配置则逆地理返回 null）"),
            Map.entry(STORAGE_ENDPOINT, "对象存储 S3 API 地址（必填），如 http://127.0.0.1:9000"),
            Map.entry(STORAGE_ACCESS_KEY, "对象存储 Access Key（必填）"),
            Map.entry(STORAGE_SECRET_KEY, "对象存储 Secret Key（必填）"),
            Map.entry(STORAGE_BUCKET, "存储桶名（必填），缺失时首次使用自动创建"),
            Map.entry(STORAGE_PRESIGN_TTL, "预签名 URL 有效期（直传 PUT 与分片共用），如 15m 或 PT15M，默认 15m")
    );

    private final SiteConfigMapper mapper;
    /**
     * key -> 当前生效值的内存缓存
     */
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    /**
     * 值为密钥的配置项：更新日志中需脱敏，避免明文入日志文件
     */
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            STORAGE_ACCESS_KEY, STORAGE_SECRET_KEY, AI_API_KEY, MAIL_PASSWORD);

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

    /**
     * 站点名称；未设置时用内置默认值
     */
    public String siteName() {
        return getString(SITE_NAME, "补陋阁");
    }

    /**
     * 留言板标题；未设置时用内置默认值
     */
    public String boardTitle() {
        return getString(BOARD_TITLE, "留言板");
    }

    /**
     * 留言板简介；未设置时用内置默认值
     */
    public String boardSummary() {
        return getString(BOARD_SUMMARY, "对网站有任何建议、想法，或者只是想打个招呼，都欢迎在这里留言。");
    }

    // ---------- AI 概要 ----------

    /**
     * 功能是否可用：总开关打开且 API Key 已配置
     */
    public boolean aiSummaryEnabled() {
        return "true".equalsIgnoreCase(getString(AI_SUMMARY_ENABLED, "false"))
                && !getString(AI_API_KEY, "").isBlank();
    }

    public String aiApiKey() {
        return getString(AI_API_KEY, null);
    }

    /**
     * AI 服务地址：Spring AI 的 OpenAiChatModel 默认在 baseUrl 后追加
     * /v1/chat/completions，因此这里要求 baseUrl 为「不含 /v1 的 API 根」。
     * 为兼容用户按旧习惯填入带 /v1 的地址（或末尾多余斜杠），统一规范化：
     * 去掉末尾斜杠与末尾 /v1，避免拼出 …/v1/v1/chat/completions 这类 404。
     */
    public String aiBaseUrl() {
        return normalizeAiBaseUrl(getString(AI_BASE_URL, "https://api.openai.com"));
    }

    private static String normalizeAiBaseUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            return "https://api.openai.com";
        }
        String u = raw.trim().replaceAll("/+$", "");   // 去末尾斜杠
        u = u.replaceAll("/v1$", "");                  // 去末尾 /v1（若有）
        return u.isEmpty() ? "https://api.openai.com" : u;
    }

    public String aiModel() {
        return getString(AI_MODEL, "gpt-4o-mini");
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
        } else if (key.equals(COMMENT_AUTO_APPROVE) || key.equals(COMMENT_NOTIFY_MAIL)
                || key.equals(AI_SUMMARY_ENABLED) || key.equals(MAIL_SSL)) {
            if (!"true".equalsIgnoreCase(trimmed) && !"false".equalsIgnoreCase(trimmed)) {
                throw new BizException(ErrorCode.BAD_REQUEST, "布尔型配置只接受 true/false");
            }
            trimmed = trimmed.toLowerCase();
        } else if ((key.equals(COMMENT_OWNER_EMAIL) || key.equals(COMMENT_FROM_EMAIL))
                && !trimmed.isEmpty()
                && !trimmed.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new BizException(ErrorCode.BAD_REQUEST, "邮箱格式不正确");
        } else if (key.equals(MAIL_PORT)) {
            try {
                int port = Integer.parseInt(trimmed);
                if (port < 1 || port > 65535) {
                    throw new BizException(ErrorCode.BAD_REQUEST, "端口须为 1-65535 之间的整数");
                }
            } catch (NumberFormatException e) {
                throw new BizException(ErrorCode.BAD_REQUEST, "端口须为整数");
            }
        } else if (key.equals(SITE_URL) && !trimmed.isEmpty() && !trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            throw new BizException(ErrorCode.BAD_REQUEST, "站点地址必须以 http:// 或 https:// 开头");
        } else if (key.equals(SITE_BIRTH_DATE) && !trimmed.isEmpty() && !trimmed.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new BizException(ErrorCode.BAD_REQUEST, "建站日期格式必须为 yyyy-MM-dd");
        } else if (key.equals(STORAGE_ENDPOINT)
                && !trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            throw new BizException(ErrorCode.BAD_REQUEST, "S3 地址必须以 http:// 或 https:// 开头");
        } else if (key.equals(STORAGE_PRESIGN_TTL)) {
            try {
                DurationStyle.detectAndParse(trimmed);
            } catch (IllegalArgumentException e) {
                throw new BizException(ErrorCode.BAD_REQUEST, "有效期格式不正确，如 15m / 30s / PT15M");
            }
        }

        mapper.upsert(key, trimmed);
        cache.put(key, trimmed);
        // 密钥类配置只记 key 不记值，防止 access-key / secret-key / api-key 泄露到日志
        log.info("site config updated: {}={}", key, SENSITIVE_KEYS.contains(key) ? "***" : trimmed);
        return new SettingDtos.SettingResponse(key, trimmed, KNOWN_KEYS.get(key));
    }
}

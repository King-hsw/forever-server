package com.forever.server.moment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forever.server.auth.RbacService;
import com.forever.server.auth.SysUser;
import com.forever.server.auth.SysUserMapper;
import com.forever.server.comment.CommentMapper;
import com.forever.server.comment.CommentService;
import com.forever.server.common.BizException;
import com.forever.server.common.ErrorCode;
import com.forever.server.common.PageResult;
import com.forever.server.common.Strings;
import com.forever.server.setting.SiteConfigService;
import com.forever.server.storage.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 动态（朋友圈）：公开时间线、发布/删除（作者或 ADMIN）、点赞、媒体上传、高德逆地理。
 * 评论复用评论模块（target_type = MOMENT，见 {@link CommentService}）。
 */
@Slf4j
@Service
public class MomentService {

    static final int MAX_IMAGES = 9;
    static final String MOMENT_TARGET = CommentService.TARGET_MOMENT;

    /** 媒体白名单：Content-Type -> 扩展名（校验与落盘共用） */
    private static final Map<String, String> EXT_BY_CONTENT_TYPE = Map.ofEntries(
            Map.entry("image/jpeg", ".jpg"),
            Map.entry("image/png", ".png"),
            Map.entry("image/webp", ".webp"),
            Map.entry("image/gif", ".gif"),
            Map.entry("audio/mpeg", ".mp3"),
            Map.entry("audio/mp4", ".m4a"),
            Map.entry("audio/wav", ".wav"),
            Map.entry("video/mp4", ".mp4"),
            Map.entry("video/webm", ".webm"));

    private static final long IMAGE_MAX_BYTES = 5L * 1024 * 1024;
    private static final long AUDIO_MAX_BYTES = 20L * 1024 * 1024;
    private static final long VIDEO_MAX_BYTES = 100L * 1024 * 1024;

    private static final HttpClient GEOCODE_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** 直传暂存 key 校验：tmp/{yyyy/MM}/{32位uuid}.{白名单扩展名}，防伪造任意对象收口 */
    private static final Pattern TMP_KEY = Pattern.compile("^tmp/\\d{4}/\\d{2}/[0-9a-f]{32}\\.(?:"
            + EXT_BY_CONTENT_TYPE.values().stream()
                    .map(ext -> ext.substring(1))
                    .collect(Collectors.joining("|"))
            + ")$");

    static final DateTimeFormatter FORMATTER_YYYY_MM = DateTimeFormatter.ofPattern("yyyy/MM");

    private final MomentMapper momentMapper;
    private final SysUserMapper sysUserMapper;
    private final CommentMapper commentMapper;
    private final RbacService rbacService;
    private final SiteConfigService siteConfig;
    private final StorageService storage;
    /** 仅用于 media JSON 存取；Spring Boot 4 自动装配的是 Jackson 3，这里用 classpath 自带的 Jackson 2 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MomentService(MomentMapper momentMapper,
                         SysUserMapper sysUserMapper,
                         CommentMapper commentMapper,
                         RbacService rbacService,
                         SiteConfigService siteConfig,
                         StorageService storage) {
        this.momentMapper = momentMapper;
        this.sysUserMapper = sysUserMapper;
        this.commentMapper = commentMapper;
        this.rbacService = rbacService;
        this.siteConfig = siteConfig;
        this.storage = storage;
    }

    // ---------- 公开端 ----------

    /**
     * 动态时间线，created_at 倒序；userUid 可选，只查该用户。
     * viewerUid 为当前访问者（可空，匿名按 null），liked/canDelete 按其计算。
     */
    public PageResult<MomentResponse> page(Long viewerUid, Long userUid, int page, int size) {
        int offset = (page - 1) * size;
        List<Moment> moments = momentMapper.page(userUid, offset, size);
        long total = momentMapper.count(userUid);
        if (moments.isEmpty()) {
            return PageResult.of(List.of(), total, page, size);
        }
        List<Long> ids = moments.stream().map(Moment::getId).toList();
        List<Long> uids = moments.stream().map(Moment::getUid).distinct().toList();
        Map<Long, SysUser> authorById = sysUserMapper.findByIds(uids).stream()
                .collect(Collectors.toMap(SysUser::getId, u -> u));
        Map<Long, Long> likeCountById = momentMapper.likeCountsByMomentIds(ids).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row.get("momentId"), row -> (Long) row.get("likeCount")));
        Map<Long, Long> commentCountById = momentMapper.commentCountsByMomentIds(ids).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row.get("momentId"), row -> (Long) row.get("commentCount")));
        Set<Long> liked = viewerUid == null ? Set.of()
                : new HashSet<>(momentMapper.likedMomentIds(viewerUid, ids));
        boolean viewerAdmin = viewerUid != null && rbacService.roleCodesOf(viewerUid).contains("ADMIN");

        List<MomentResponse> items = moments.stream().map(m -> {
            SysUser author = authorById.get(m.getUid());
            return new MomentResponse(
                    m.getId(), m.getUid(),
                    author == null ? null : author.getUsername(),
                    author == null ? null : author.getAvatarUrl(),
                    m.getContent(), readMedia(m.getMedia()),
                    m.getLocation(),
                    m.getLat() == null ? null : m.getLat().doubleValue(),
                    m.getLng() == null ? null : m.getLng().doubleValue(),
                    m.getCreatedAt(),
                    likeCountById.getOrDefault(m.getId(), 0L),
                    liked.contains(m.getId()),
                    commentCountById.getOrDefault(m.getId(), 0L),
                    viewerUid != null && (viewerUid.equals(m.getUid()) || viewerAdmin));
        }).toList();
        return PageResult.of(items, total, page, size);
    }

    // ---------- 管理端 ----------

    /**
     * 申请直传地址：按 Content-Type 白名单生成 tmp/{yyyy/MM}/{uuid}.{ext} key，
     * 签发限时 PUT URL；前端直传后把 key 回传给 create 收口为正式对象。
     */
    public MomentDtos.PresignUploadResponse presignUpload(String contentTypeRaw) {
        String contentType = normalizeContentType(contentTypeRaw);
        String ext = EXT_BY_CONTENT_TYPE.get(contentType);
        if (ext == null) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "仅支持 jpg / png / webp / gif 图片、mp3 / m4a / wav 音频、mp4 / webm 视频");
        }
        String key = "tmp/" + FORMATTER_YYYY_MM.format(YearMonth.now())
                + "/" + UUID.randomUUID().toString().replace("-", "") + ext;
        String url = storage.presignPutUrl(key, contentType, storage.presignTtl());
        log.debug("presigned upload: key={}, contentType={}, ttl={}s", key, contentType,
                storage.presignTtl().toSeconds());
        return new MomentDtos.PresignUploadResponse(
                url, key, contentType, storage.presignTtl().toSeconds());
    }

    public MomentResponse create(long uid, MomentCreateRequest request) {
        String content = Strings.blankToNull(request.content());
        List<String> images = request.images() == null ? List.of()
                : request.images().stream().map(Strings::blankToNull).filter(u -> u != null)
                        .map(this::resolveMedia).toList();
        String audio = resolveMedia(Strings.blankToNull(request.audio()));
        String video = resolveMedia(Strings.blankToNull(request.video()));
        if (images.size() > MAX_IMAGES) {
            throw new BizException(ErrorCode.BAD_REQUEST, "图片最多 9 张");
        }
        if (content == null && images.isEmpty() && audio == null && video == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "内容、图片、音频、视频至少填一项");
        }
        Moment m = new Moment();
        m.setUid(uid);
        m.setContent(content == null ? "" : content);
        m.setMedia(writeMedia(new MomentMedia(images, audio, video)));
        m.setLocation(Strings.blankToNull(request.location()));
        m.setLat(request.lat() == null ? null : BigDecimal.valueOf(request.lat()));
        m.setLng(request.lng() == null ? null : BigDecimal.valueOf(request.lng()));
        m.setCreatedAt(LocalDateTime.now());
        momentMapper.insert(m);
        log.info("moment created: id={}, uid={}", m.getId(), uid);
        SysUser author = sysUserMapper.findById(uid);
        return new MomentResponse(m.getId(), uid,
                author == null ? null : author.getUsername(),
                author == null ? null : author.getAvatarUrl(),
                m.getContent(), new MomentMedia(images, audio, video),
                m.getLocation(), request.lat(), request.lng(), m.getCreatedAt(),
                0, false, 0, true);
    }

    public void delete(long uid, Long id) {
        Moment moment = requireExists(id);
        if (!moment.getUid().equals(uid)
                && !rbacService.roleCodesOf(uid).contains("ADMIN")) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        momentMapper.deleteById(id);
        momentMapper.deleteByMomentId(id);
        commentMapper.deleteByTarget(MOMENT_TARGET, id);
        log.info("moment deleted: id={}, by={}", id, uid);
    }

    /** 点赞；重复点幂等 */
    public MomentDtos.LikeResponse like(long uid, Long momentId) {
        requireExists(momentId);
        momentMapper.insertLike(momentId, uid);
        log.debug("moment liked: momentId={}, uid={}", momentId, uid);
        return new MomentDtos.LikeResponse(momentMapper.countLikes(momentId), true);
    }

    /** 取消点赞；未点过幂等 */
    public MomentDtos.LikeResponse unlike(long uid, Long momentId) {
        requireExists(momentId);
        momentMapper.deleteLike(momentId, uid);
        log.debug("moment unliked: momentId={}, uid={}", momentId, uid);
        return new MomentDtos.LikeResponse(momentMapper.countLikes(momentId), false);
    }

    /**
     * 媒体上传：按 Content-Type 白名单校验，存为 moment/{yyyy/MM}/{uuid}.{ext}，
     * 返回 /uploads/moment/... 访问地址（统一写入对象存储，读取时 302）。
     */
    public MomentDtos.UploadResponse upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "未选择文件");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().trim().toLowerCase();
        String ext = EXT_BY_CONTENT_TYPE.get(contentType);
        if (ext == null) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "仅支持 jpg / png / webp / gif 图片、mp3 / m4a / wav 音频、mp4 / webm 视频");
        }
        if (file.getSize() > maxBytesFor(contentType)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "文件大小超出限制");
        }
        YearMonth yearMonth = YearMonth.now();
        String relative = "moment/" + FORMATTER_YYYY_MM.format(yearMonth)
                + "/" + UUID.randomUUID().toString().replace("-", "") + ext;
        String url;
        try {
            url = storage.save(relative, contentType, file.getInputStream(), file.getSize());
        } catch (IOException e) {
            log.error("保存动态媒体失败: {}", relative, e);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "文件保存失败");
        }
        log.info("moment media uploaded: url={}, size={}B", url, file.getSize());
        return new MomentDtos.UploadResponse(url);
    }

    /**
     * 高德 Web Service 逆地理（裸 HTTP，不加依赖）；
     * 未配置 key / 参数缺失 / 网络或高德报错一律静默返回 text=null。
     */
    public MomentDtos.GeocodeResponse geocode(Double lat, Double lng) {
        String key = siteConfig.getString(SiteConfigService.MOMENTS_AMAP_KEY, null);
        if (key == null || lat == null || lng == null) {
            return new MomentDtos.GeocodeResponse(null);
        }
        try {
            String url = "https://restapi.amap.com/v3/geocode/regeo?extensions=base"
                    + "&key=" + URLEncoder.encode(key, StandardCharsets.UTF_8)
                    + "&location=" + lng + "," + lat;
            HttpResponse<String> resp = GEOCODE_CLIENT.send(
                    HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(resp.body());
            if (!"1".equals(root.path("status").asText())) {
                // 高德业务失败（配额超限/参数问题等），info 字段带原因；对前端表现为无地点
                log.debug("amap regeo rejected: info={}", root.path("info").asText());
                return new MomentDtos.GeocodeResponse(null);
            }
            JsonNode comp = root.path("regeocode").path("addressComponent");
            String city = cityText(comp.path("city"));
            String district = comp.path("district").asText("");
            String text = district.isEmpty() || district.equals(city) ? city : city + district;
            return new MomentDtos.GeocodeResponse(Strings.blankToNull(text));
        } catch (Exception e) {
            log.warn("高德逆地理调用失败: {}", e.toString());
            return new MomentDtos.GeocodeResponse(null);
        }
    }

    // ---------- internal ----------

    /**
     * 兼容两种媒体入参：正式 /uploads/moment/... 地址原样保留（服务端中转上传的历史流程）；
     * tmp/ 开头的直传 key 收口为正式对象——HeadObject 复核类型与大小，
     * CopyObject 到 moment/{yyyy/MM}/{uuid}.{ext} 后删除暂存对象。
     */
    private String resolveMedia(String value) {
        if (value == null || !value.startsWith(StorageService.TMP_PREFIX)) {
            return value;
        }
        if (!TMP_KEY.matcher(value).matches()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "非法的直传文件标识: " + value);
        }
        StorageService.Stat stat = storage.stat(value);
        if (stat == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "直传文件不存在或已过期，请重新上传");
        }
        String contentType = normalizeContentType(stat.contentType());
        String ext = EXT_BY_CONTENT_TYPE.get(contentType);
        if (ext == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "直传文件类型不在支持范围内");
        }
        if (stat.size() > maxBytesFor(contentType)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "直传文件大小超出限制");
        }
        String relative = "moment/" + FORMATTER_YYYY_MM.format(YearMonth.now())
                + "/" + UUID.randomUUID().toString().replace("-", "") + ext;
        storage.copy(value, relative, contentType);
        storage.delete(value);
        log.info("moment media finalized: {} -> {}", value, relative);
        return StorageService.urlOf(relative);
    }

    /** MIME 归一化：去参数、转小写，如 "image/png; charset=x" -> "image/png" */
    private static String normalizeContentType(String contentType) {
        return contentType == null ? "" : contentType.split(";")[0].trim().toLowerCase();
    }

    private static long maxBytesFor(String contentType) {
        if (contentType.startsWith("image/")) {
            return IMAGE_MAX_BYTES;
        }
        if (contentType.startsWith("audio/")) {
            return AUDIO_MAX_BYTES;
        }
        return VIDEO_MAX_BYTES;
    }

    /** Amap 直辖市 city 为数组（如 ["北京市"]），普通城市为字符串 */
    private static String cityText(JsonNode city) {
        if (city.isTextual()) {
            return city.asText().trim();
        }
        if (city.isArray() && !city.isEmpty()) {
            return city.get(0).asText().trim();
        }
        return "";
    }

    private Moment requireExists(Long id) {
        Moment moment = momentMapper.findById(id);
        if (moment == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "动态不存在");
        }
        return moment;
    }

    private MomentMedia readMedia(String json) {
        if (json == null || json.isBlank()) {
            return new MomentMedia(List.of(), null, null);
        }
        try {
            MomentMedia media = objectMapper.readValue(json, MomentMedia.class);
            return media == null ? new MomentMedia(List.of(), null, null) : media;
        } catch (IOException e) {
            log.warn("动态媒体 JSON 解析失败: {}", json);
            return new MomentMedia(List.of(), null, null);
        }
    }

    private String writeMedia(MomentMedia media) {
        try {
            return objectMapper.writeValueAsString(media);
        } catch (IOException e) {
            throw new IllegalStateException("序列化媒体 JSON 失败", e);
        }
    }
}

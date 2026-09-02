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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 动态（朋友圈）：公开时间线、发布/删除（作者或 ADMIN）、高德逆地理。
 * 媒体引用为前端直传得到的 RustFS 直链，发布时仅做 http(s) 格式校验——
 * 文件状态由存储自持，业务库不记录。
 * 评论复用评论模块（target_type = MOMENT，见 {@link CommentService}）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MomentService {

    static final int MAX_IMAGES = 9;
    static final String MOMENT_TARGET = CommentService.TARGET_MOMENT;

    private static final HttpClient GEOCODE_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final MomentMapper momentMapper;
    private final SysUserMapper sysUserMapper;
    private final CommentMapper commentMapper;
    private final RbacService rbacService;
    private final SiteConfigService siteConfig;
    /**
     * 仅用于 media JSON 存取；Spring Boot 4 自动装配的是 Jackson 3，这里用 classpath 自带的 Jackson 2
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ---------- 公开端 ----------

    /**
     * 动态时间线，created_at 倒序；userUid 可选，只查该用户。
     * viewerUid 为当前访问者（可空，匿名按 null），canDelete 按其计算。
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
        Map<Long, Long> commentCountById = momentMapper.commentCountsByMomentIds(ids).stream()
                .collect(Collectors.toMap(
                        row -> (Long) row.get("momentId"), row -> (Long) row.get("commentCount")));
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
                    commentCountById.getOrDefault(m.getId(), 0L),
                    viewerUid != null && (viewerUid.equals(m.getUid()) || viewerAdmin));
        }).toList();
        return PageResult.of(items, total, page, size);
    }

    // ---------- 管理端 ----------

    /**
     * 发布动态：媒体引用为前端直传得到的 RustFS 直链，仅校验 http(s) 格式后入库
     */
    public MomentResponse create(long uid, MomentCreateRequest request) {
        String content = Strings.blankToNull(request.content());
        List<String> images = request.images() == null ? List.of()
                : request.images().stream().map(Strings::blankToNull).filter(u -> u != null)
                .map(ref -> Strings.checkHttpUrl(ref, "图片直链")).toList();
        String audio = Strings.blankToNull(request.audio());
        if (audio != null) {
            audio = Strings.checkHttpUrl(audio, "音频直链");
        }
        String video = Strings.blankToNull(request.video());
        if (video != null) {
            video = Strings.checkHttpUrl(video, "视频直链");
        }
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
                0, true);
    }

    public void delete(long uid, Long id) {
        Moment moment = requireExists(id);
        if (!moment.getUid().equals(uid)
                && !rbacService.roleCodesOf(uid).contains("ADMIN")) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        momentMapper.deleteById(id);
        commentMapper.deleteByTarget(MOMENT_TARGET, id);
        log.info("moment deleted: id={}, by={}", id, uid);
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

    /**
     * Amap 直辖市 city 为数组（如 ["北京市"]），普通城市为字符串
     */
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

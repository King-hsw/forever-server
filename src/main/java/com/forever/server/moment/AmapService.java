package com.forever.server.moment;

import com.forever.server.common.Strings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 高德（Amap）Web Service 第三方调用（裸 HTTP，不加依赖）：逆地理（lat/lng → 城市+区县文本）。
 * key 在 yml（blog.moments.amap.key）；未配置 / 高德或网络报错一律静默返回 null——
 * 动态页「获取当前位置」静默降级，用户可手动填写地点。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AmapService {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final AmapProperties props;
    /** 仅用于解析高德响应 JSON，Spring Boot 4 自动装配的 Jackson 3（tools.jackson） */
    private final ObjectMapper objectMapper;

    /**
     * 逆地理，返回「城市+区县」文本；key 未配置或调用失败返回 null
     */
    public String regeoText(double lat, double lng) {
        String key = props.key();
        if (key == null || key.isBlank()) {
            return null;
        }
        try {
            String url = "https://restapi.amap.com/v3/geocode/regeo?extensions=base"
                    + "&key=" + URLEncoder.encode(key, StandardCharsets.UTF_8)
                    + "&location=" + lng + "," + lat;
            HttpResponse<String> resp = CLIENT.send(
                    HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(resp.body());
            if (!"1".equals(root.path("status").asText())) {
                // 高德业务失败（配额超限/参数问题等），info 字段带原因；对前端表现为无地点
                log.debug("amap regeo rejected: info={}", root.path("info").asText());
                return null;
            }
            JsonNode comp = root.path("regeocode").path("addressComponent");
            String city = cityText(comp.path("city"));
            String district = comp.path("district").asText("");
            String text = district.isEmpty() || district.equals(city) ? city : city + district;
            return Strings.blankToNull(text);
        } catch (Exception e) {
            log.warn("高德逆地理调用失败: {}", e.toString());
            return null;
        }
    }

    /**
     * 高德直辖市 city 为数组（如 ["北京市"]），普通城市为字符串
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
}

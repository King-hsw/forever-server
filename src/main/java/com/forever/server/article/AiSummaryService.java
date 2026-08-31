package com.forever.server.article;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.forever.server.common.BizException;
import com.forever.server.common.ErrorCode;
import com.forever.server.setting.SiteConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * AI 文章概要：调 OpenAI 兼容接口为文章正文生成摘要，写入 article.summary。
 * 配置（开关/Key/地址/模型）全部来自后台站点设置，运行时修改即时生效；
 * 未开启或未配置 Key 时功能不可用。每次调用按当前配置构建请求。
 */
@Slf4j
@Service
public class AiSummaryService {

    /** 送入模型的正文字符上限，超出截断 */
    private static final int MAX_CONTENT_CHARS = 8000;

    /** 等待模型回复的超时时间 */
    private static final Duration MODEL_TIMEOUT = Duration.ofSeconds(120);

    private static final String INSTRUCTION = """
            你是博客文章编辑。请阅读下面的文章内容，用中文写一段不超过 120 字的摘要，
            作为文章列表页展示的概要。只输出摘要纯文本，不要任何前缀、引号或解释。

            文章标题：%s

            正文：
            %s
            """;

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ArticleMapper articleMapper;
    private final SiteConfigService siteConfig;

    public AiSummaryService(ArticleMapper articleMapper, SiteConfigService siteConfig) {
        this.articleMapper = articleMapper;
        this.siteConfig = siteConfig;
    }

    /** 生成概要并保存，返回新概要文本 */
    public String generate(Long articleId) {
        if (!siteConfig.aiSummaryEnabled()) {
            throw new BizException(ErrorCode.CONFLICT, "AI 概要功能未开启或未配置 API Key");
        }
        Article article = articleMapper.findById(articleId);
        if (article == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "文章不存在");
        }
        // ponytail: 超长正文直接截断前 8000 字，够生成摘要；分块/精读等需求出现再升级
        String content = article.getContent() == null ? "" : article.getContent();
        if (content.length() > MAX_CONTENT_CHARS) {
            content = content.substring(0, MAX_CONTENT_CHARS);
        }

        String summary = callModel(siteConfig, article.getTitle(), content);
        articleMapper.updateSummary(articleId, summary);
        log.info("ai summary generated: id={}, chars={}", articleId, summary.length());
        return summary;
    }

    /**
     * 直接 POST {baseUrl}/chat/completions 取 choices[0].message.content。
     * 不用 Spring AI SDK：其 openai-java 客户端强制要求响应带 OpenAI 官方字段
     * （id/created/model 等），讯飞星火等 OpenAI 兼容实现不返回这些字段，解析必挂。
     */
    private String callModel(SiteConfigService cfg, String title, String content) {
        String url = stripTrailingSlash(cfg.aiBaseUrl()) + "/chat/completions";
        ObjectNode body = OBJECT_MAPPER.createObjectNode()
                .put("model", cfg.aiModel())
                .put("stream", false);
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "user").put("content", INSTRUCTION.formatted(title, content));

        HttpResponse<String> response;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(MODEL_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + cfg.aiApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.INTERNAL_ERROR, "AI 概要生成失败：请求被中断");
        } catch (IOException e) {
            log.error("ai summary call failed", e);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "AI 概要生成失败：" + e.getMessage());
        }

        int status = response.statusCode();
        String responseBody = response.body() == null ? "" : response.body();
        if (status / 100 != 2) {
            log.error("ai summary call failed: url={}, status={}", url, status);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "AI 概要生成失败：HTTP " + status + " " + abbreviate(responseBody));
        }
        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(responseBody);
        } catch (IOException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "AI 概要生成失败：响应不是合法 JSON");
        }
        JsonNode textNode = root.at("/choices/0/message/content");
        if (textNode.isMissingNode() || textNode.isNull() || textNode.asText("").isBlank()) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "AI 概要生成失败：" + abbreviate(responseBody));
        }
        return textNode.asText().trim();
    }

    private static String stripTrailingSlash(String url) {
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    private static String abbreviate(String s) {
        String trimmed = s.trim();
        return trimmed.length() > 300 ? trimmed.substring(0, 300) : trimmed;
    }
}

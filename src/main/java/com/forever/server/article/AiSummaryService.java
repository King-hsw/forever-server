package com.forever.server.article;

import com.forever.server.common.BizException;
import com.forever.server.common.ErrorCode;
import com.forever.server.setting.SiteConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

/**
 * AI 文章概要：调 OpenAI 兼容接口为文章正文生成摘要，写入 article.summary。
 * 配置（开关/Key/地址/模型）全部来自后台站点设置，运行时修改即时生效；
 * 未开启或未配置 Key 时功能不可用。每次调用按当前配置构建客户端。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiSummaryService {

    /**
     * 送入模型的正文字符上限，超出截断
     */
    private static final int MAX_CONTENT_CHARS = 8000;

    private static final String INSTRUCTION = """
            你是博客文章编辑。请阅读下面的文章内容，用中文写一段不超过 120 字的摘要，
            作为文章列表页展示的概要。只输出摘要纯文本，不要任何前缀、引号或解释。
            
            文章标题：%s
            
            正文：
            %s
            """;

    private final ArticleMapper articleMapper;
    private final SiteConfigService siteConfig;

    /**
     * 生成概要并保存，返回新概要文本
     */
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

    private String callModel(SiteConfigService cfg, String title, String content) {
        try {
            // baseUrl/apiKey 必须写在 options 上：OpenAiChatModel 按 options 构建同步+异步客户端，
            // 只塞自建 client 时 options 缺 apiKey 会在 build() 抛 credential 缺失
            OpenAiChatModel model = OpenAiChatModel.builder()
                    .options(OpenAiChatOptions.builder()
                            .baseUrl(cfg.aiBaseUrl())
                            .apiKey(cfg.aiApiKey())
                            .model(cfg.aiModel())
                            .build())
                    .build();
            return model.call(new Prompt(INSTRUCTION.formatted(title, content)))
                    .getResult().getOutput().getText().trim();
        } catch (Exception e) {
            log.error("ai summary call failed", e);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "AI 概要生成失败：" + e.getMessage());
        }
    }
}

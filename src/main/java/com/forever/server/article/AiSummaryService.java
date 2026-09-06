package com.forever.server.article;

import com.forever.server.ai.AiClient;
import com.forever.server.common.BizException;
import com.forever.server.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * AI 文章概要：为文章正文生成摘要，写入 article.summary。
 * 大模型调用由 {@link AiClient} 负责；API Key 未配置（yml/env）时其业务异常自然传播。
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
    private final AiClient aiClient;

    /**
     * 生成概要并保存，返回新概要文本
     */
    public String generate(Long articleId) {
        Article article = articleMapper.findById(articleId);
        if (article == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "文章不存在");
        }
        // ponytail: 超长正文直接截断前 8000 字，够生成摘要；分块/精读等需求出现再升级
        String content = article.getContent() == null ? "" : article.getContent();
        if (content.length() > MAX_CONTENT_CHARS) {
            content = content.substring(0, MAX_CONTENT_CHARS);
        }

        String prompt = INSTRUCTION.formatted(article.getTitle(), content);
        String summary = aiClient.chat(prompt);
        articleMapper.updateSummary(articleId, summary);
        log.info("ai summary generated: id={}, chars={}", articleId, summary.length());
        return summary;
    }
}

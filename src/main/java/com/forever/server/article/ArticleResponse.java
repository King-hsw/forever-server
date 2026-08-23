package com.forever.server.article;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章响应：content 仅详情接口返回，列表为 null。
 */
@Schema(description = "文章信息（列表接口不返回 content）")
public record ArticleResponse(
        @Schema(description = "文章 id", example = "1") Long id,
        @Schema(description = "标题") String title,
        @Schema(description = "URL 别名") String slug,
        @Schema(description = "摘要") String summary,
        @Schema(description = "正文；仅详情接口返回，列表接口为 null") String content,
        @Schema(description = "封面图 URL") String coverImage,
        @Schema(description = "分类 id") Long categoryId,
        @Schema(description = "分类名称") String categoryName,
        @Schema(description = "标签列表") List<Article.TagItem> tags,
        @Schema(description = "状态：DRAFT-草稿 / PUBLISHED-已发布") ArticleStatus status,
        @Schema(description = "内容类型：ARTICLE-文章 / PAGE-独立页面", defaultValue = "ARTICLE") ArticleType type,
        @Schema(description = "正文格式：MARKDOWN / HTML", defaultValue = "MARKDOWN") ContentFormat contentFormat,
        @Schema(description = "浏览量") long viewCount,
        @Schema(description = "发布时间") LocalDateTime publishedAt,
        @Schema(description = "创建时间") LocalDateTime createdAt,
        @Schema(description = "更新时间") LocalDateTime updatedAt,
        @Schema(description = "前台完整 URL（需配置 blog.site.url，否则为 null）；列表接口同样返回") String url,
        @Schema(description = "预计阅读分钟数；仅详情接口返回（依赖正文），列表接口为 null", example = "6") Integer readingTime) {

        /** 按正文估算阅读时长：中文按 400 字/分，西文按 200 词/分，不足 1 分钟记 1 */
        public static Integer estimateReadingTime(String content) {
            if (content == null || content.isBlank()) {
                return null;
            }
            int cjk = 0;
            int latinWords = 0;
            boolean inWord = false;
            for (char ch : content.toCharArray()) {
                if (ch >= 0x4E00 && ch <= 0x9FFF) {
                    cjk++;
                    inWord = false;
                } else if (Character.isLetterOrDigit(ch)) {
                    if (!inWord) {
                        latinWords++;
                        inWord = true;
                    }
                } else {
                    inWord = false;
                }
            }
            long minutes = Math.round(cjk / 400.0 + latinWords / 200.0);
            return (int) Math.max(1, minutes);
        }
}

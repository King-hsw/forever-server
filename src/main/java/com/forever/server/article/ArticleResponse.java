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
        @Schema(description = "浏览量") long viewCount,
        @Schema(description = "发布时间") LocalDateTime publishedAt,
        @Schema(description = "创建时间") LocalDateTime createdAt,
        @Schema(description = "更新时间") LocalDateTime updatedAt) {
}

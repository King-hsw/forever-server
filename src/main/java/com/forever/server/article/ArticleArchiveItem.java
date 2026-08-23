package com.forever.server.article;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 文章归档项：仅含归档列表所需的最小字段。
 */
@Schema(description = "文章归档项")
public record ArticleArchiveItem(
        @Schema(description = "文章 id", example = "1") long id,
        @Schema(description = "标题") String title,
        @Schema(description = "URL 别名") String slug,
        @Schema(description = "发布时间") LocalDateTime publishedAt) {
}

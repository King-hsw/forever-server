package com.forever.server.search;

import com.forever.server.article.Article;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "全局搜索结果项")
public record SearchItemResponse(
        Long id,
        String slug,
        @Schema(description = "原始标题（未转义）") String title,
        @Schema(description = "分类名，可为 null") String categoryName,
        @Schema(description = "标签列表") List<Article.TagItem> tags,
        LocalDateTime createdAt,
        @Schema(description = "关键词高亮；标题未命中且正文/摘要也无命中时为 null") Highlights highlights) {

    @Schema(description = "高亮片段：HTML 已转义，仅 <em> 为标签，前端可放心 v-html")
    public record Highlights(
            @Schema(description = "标题高亮；标题未命中时为原文") String title,
            @Schema(description = "命中上下文片段，约 120 字符；未命中正文/摘要时为开头摘要") String excerpt) {
    }
}

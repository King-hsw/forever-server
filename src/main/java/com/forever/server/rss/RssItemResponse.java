package com.forever.server.rss;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "订阅文章条目（公开接口）")
public record RssItemResponse(
        Long id,
        @Schema(description = "来源站点名称") String feedTitle,
        @Schema(description = "来源站点主页") String siteUrl,
        @Schema(description = "文章标题") String title,
        @Schema(description = "原文链接，前端新窗口打开") String link,
        @Schema(description = "摘要（纯文本，截断至约 300 字）") String summary,
        @Schema(description = "发布时间；源未提供时为 null") LocalDateTime publishedAt) {

    static RssItemResponse from(RssItemMapper.ItemRow row) {
        return new RssItemResponse(row.id(), row.feedTitle(), row.siteUrl(),
                row.title(), row.link(), row.summary(), row.publishedAt());
    }
}

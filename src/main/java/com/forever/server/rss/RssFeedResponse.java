package com.forever.server.rss;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "订阅源信息（管理端，含抓取状态与条目数）")
public record RssFeedResponse(
        Long id,
        @Schema(description = "站点名称") String title,
        @Schema(description = "博客主页地址") String siteUrl,
        @Schema(description = "RSS/Atom 地址") String feedUrl,
        String description,
        @Schema(description = "是否启用抓取") boolean enabled,
        @Schema(description = "已抓取的条目数") long itemCount,
        @Schema(description = "上次抓取时间；从未抓取为 null") LocalDateTime lastFetchedAt,
        @Schema(description = "上次抓取的错误信息；正常为 null") String lastError) {
}

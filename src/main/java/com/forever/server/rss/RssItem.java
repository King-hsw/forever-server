package com.forever.server.rss;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 从订阅源抓取到的文章条目。feedTitle 仅供联表查询展示，不落库。
 */
@Data
public class RssItem {

    private Long id;
    private Long feedId;
    private String feedTitle;
    private String title;
    private String link;
    private String summary;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
}

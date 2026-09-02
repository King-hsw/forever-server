package com.forever.server.rss;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * RSS 订阅源。
 */
@Data
public class RssFeed {

    private Long id;
    /**
     * 站点名称；创建时可不填，抓取成功后自动回填 feed 自带标题
     */
    private String title;
    private String siteUrl;
    private String feedUrl;
    private String description;
    private Boolean enabled;
    private LocalDateTime lastFetchedAt;
    private String lastError;
    private LocalDateTime createdAt;
}

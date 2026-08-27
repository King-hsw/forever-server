package com.forever.server.rss;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时抓取调度器。
 * 默认启动 1 分钟后首抓，之后每 6 小时一次（fixedDelay 保证不与上一轮重叠）；
 * 可用配置覆盖：blog.rss.initial-delay-ms / blog.rss.fetch-interval-ms。
 */
@Component
public class RssScheduler {

    private final RssFetchService fetchService;

    public RssScheduler(RssFetchService fetchService) {
        this.fetchService = fetchService;
    }

    @Scheduled(
            initialDelayString = "${blog.rss.initial-delay-ms:60000}",
            fixedDelayString = "${blog.rss.fetch-interval-ms:21600000}")
    public void fetch() {
        fetchService.fetchAll();
    }
}

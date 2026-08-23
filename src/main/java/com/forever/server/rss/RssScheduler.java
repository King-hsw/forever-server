package com.forever.server.rss;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 定时抓取调度器。
 * 默认启动 1 分钟后首抓，之后每 6 小时一次；
 * 可用配置覆盖：blog.rss.initial-delay-ms / blog.rss.fetch-interval-ms。
 */
@Slf4j
@Component
public class RssScheduler {

    private final RssFetchService fetchService;
    /** 上一次还没跑完时跳过本轮，防止任务堆积 */
    private final AtomicBoolean running = new AtomicBoolean(false);

    public RssScheduler(RssFetchService fetchService) {
        this.fetchService = fetchService;
    }

    @Scheduled(
            initialDelayString = "${blog.rss.initial-delay-ms:60000}",
            fixedDelayString = "${blog.rss.fetch-interval-ms:21600000}")
    public void fetch() {
        if (!running.compareAndSet(false, true)) {
            log.warn("rss fetch skipped: previous run still in progress");
            return;
        }
        try {
            fetchService.fetchAll();
        } finally {
            running.set(false);
        }
    }
}

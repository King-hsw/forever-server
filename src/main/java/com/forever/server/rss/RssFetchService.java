package com.forever.server.rss;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

/**
 * RSS 抓取：JDK HttpClient 拉取 + Rome 解析（兼容 RSS 2.0 / Atom）。
 * 条目按 (feed_id, link) 幂等写入，重复抓取不会产生脏数据。
 */
@Slf4j
@Service
public class RssFetchService {

    private static final int SUMMARY_MAX = 300;
    private static final int MAX_ITEMS_PER_FETCH = 50;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final RssFeedMapper feedMapper;
    private final RssItemMapper itemMapper;

    public RssFetchService(RssFeedMapper feedMapper, RssItemMapper itemMapper) {
        this.feedMapper = feedMapper;
        this.itemMapper = itemMapper;
    }

    /** 抓取所有启用的源（定时任务 / 手动全量刷新入口） */
    public void fetchAll() {
        List<RssFeed> feeds = feedMapper.findEnabled();
        log.info("rss fetch start: {} enabled feed(s)", feeds.size());
        int ok = 0;
        for (RssFeed feed : feeds) {
            if (fetchOne(feed)) {
                ok++;
            }
        }
        log.info("rss fetch done: {}/{} succeeded", ok, feeds.size());
    }

    /**
     * 抓取单个源。失败不抛异常，只记录到 last_error，避免影响其他源。
     *
     * @return 是否成功
     */
    public boolean fetchOne(RssFeed feed) {
        try {
            SyndFeed synd = download(feed.getFeedUrl());
            int inserted = saveEntries(feed.getId(), synd);
            feedMapper.markSuccess(feed.getId(), synd.getTitle(), synd.getDescription(), LocalDateTime.now());
            log.info("rss fetched: id={}, title={}, new items={}", feed.getId(),
                    synd.getTitle(), inserted);
            return true;
        } catch (Exception e) {
            String error = truncate(e.getClass().getSimpleName() + ": " + e.getMessage(), 500);
            feedMapper.markError(feed.getId(), error, LocalDateTime.now());
            log.warn("rss fetch failed: id={}, url={}, reason={}", feed.getId(), feed.getFeedUrl(), error);
            return false;
        }
    }

    private SyndFeed download(String feedUrl) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(feedUrl))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "forever-server-rss/0.1 (+personal blog aggregator)")
                .GET()
                .build();
        HttpResponse<InputStream> response =
                http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        // XmlReader 会自动探测编码；try-with-resources 关闭响应流
        try (InputStream body = response.body()) {
            return new SyndFeedInput().build(new XmlReader(body));
        }
    }

    /** 只保留每条 entry 的最新 MAX_ITEMS_PER_FETCH 条，历史旧文不回灌 */
    private int saveEntries(Long feedId, SyndFeed synd) {
        List<SyndEntry> entries = synd.getEntries();
        int inserted = 0;
        for (SyndEntry entry : entries) {
            if (inserted >= MAX_ITEMS_PER_FETCH) {
                break;
            }
            String link = entry.getLink();
            String title = entry.getTitle();
            if (link == null || link.isBlank() || title == null || title.isBlank()) {
                continue;
            }
            RssItem item = new RssItem();
            item.setFeedId(feedId);
            item.setLink(truncate(link, 500));
            item.setTitle(truncate(title, 500));
            item.setSummary(extractSummary(entry));
            item.setPublishedAt(toLocalDateTime(
                    entry.getPublishedDate() != null ? entry.getPublishedDate() : entry.getUpdatedDate()));
            inserted += itemMapper.insertIgnore(item);
        }
        return inserted;
    }

    /** 取描述文本并去掉内嵌 HTML 标签 */
    private String extractSummary(SyndEntry entry) {
        if (entry.getDescription() == null) {
            return null;
        }
        String text = entry.getDescription().getValue();
        if (text == null) {
            return null;
        }
        text = text.replaceAll("<[^>]*>", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return truncate(text, SUMMARY_MAX);
    }

    private static LocalDateTime toLocalDateTime(Date date) {
        return date == null ? null
                : LocalDateTime.ofInstant(date.toInstant(), java.time.ZoneId.systemDefault());
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}

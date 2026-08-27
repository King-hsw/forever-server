package com.forever.server.rss;

import com.forever.server.article.Article;
import com.forever.server.article.ArticleMapper;
import com.forever.server.setting.SiteConfigService;
import com.rometools.rome.feed.synd.SyndContentImpl;
import com.rometools.rome.feed.synd.SyndEntryImpl;
import com.rometools.rome.feed.synd.SyndFeedImpl;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedOutput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneId;
import java.util.Date;
import java.util.List;

/**
 * 对外输出本站 RSS feed：别人可以把 /rss 地址加进阅读器订阅本站。
 * 数据源为已发布文章，取最新 20 篇。
 */
@Tag(name = "RSS·本站输出", description = "对外提供本站文章的 RSS 订阅源")
@RestController
public class SiteFeedController {

    private static final int FEED_SIZE = 20;

    private final ArticleMapper articleMapper;
    private final SiteConfigService siteConfig;

    public SiteFeedController(ArticleMapper articleMapper, SiteConfigService siteConfig) {
        this.articleMapper = articleMapper;
        this.siteConfig = siteConfig;
    }

    @Operation(summary = "本站 RSS 订阅源", description = "RSS 2.0 格式，返回最新 20 篇已发布文章；" +
            "文章链接基于后台站点设置 site.url 拼接")
    @GetMapping(value = "/rss", produces = MediaType.APPLICATION_RSS_XML_VALUE)
    public ResponseEntity<String> rss() throws com.rometools.rome.io.FeedException {
        String siteUrl = trimTrailingSlash(siteConfig.getString(SiteConfigService.SITE_URL, ""));

        String siteName = siteConfig.siteName();

        SyndFeed feed = new SyndFeedImpl();
        feed.setFeedType("rss_2.0");
        feed.setTitle(siteName);
        feed.setLink(siteUrl);
        feed.setDescription(siteName + " - 个人博客最新文章");

        List<Article> articles =
                articleMapper.publicPage(null, null, null, 0, FEED_SIZE);
        for (Article a : articles) {
            SyndEntry entry = new SyndEntryImpl();
            entry.setTitle(a.getTitle());
            entry.setLink(siteUrl + "/articles/" + a.getSlug());
            entry.setUri(siteUrl + "/articles/" + a.getSlug());
            Date published = toDate(a.getPublishedAt() != null ? a.getPublishedAt() : a.getCreatedAt());
            entry.setPublishedDate(published);
            entry.setUpdatedDate(toDate(a.getUpdatedAt()));

            SyndContentImpl description = new SyndContentImpl();
            description.setType("text/plain");
            description.setValue(a.getSummary() != null ? a.getSummary() : a.getTitle());
            entry.setDescription(description);
            feed.getEntries().add(entry);
        }

        String xml = new SyndFeedOutput().outputString(feed);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(MediaType.APPLICATION_RSS_XML_VALUE + ";charset=UTF-8"))
                .body(xml);
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static Date toDate(java.time.LocalDateTime t) {
        return t == null ? null : Date.from(t.atZone(ZoneId.systemDefault()).toInstant());
    }
}

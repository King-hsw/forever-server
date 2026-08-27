package com.forever.server.rss;

import com.forever.server.common.BizException;
import com.forever.server.common.ErrorCode;
import com.forever.server.common.PageResult;
import com.forever.server.common.Strings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class RssService {

    private final RssFeedMapper feedMapper;
    private final RssItemMapper itemMapper;
    private final RssFetchService fetchService;

    public RssService(RssFeedMapper feedMapper,
                      RssItemMapper itemMapper,
                      RssFetchService fetchService) {
        this.feedMapper = feedMapper;
        this.itemMapper = itemMapper;
        this.fetchService = fetchService;
    }

    // ---------- 管理端 ----------

    public PageResult<RssItemResponse> pageItems(int page, int size) {
        int offset = (page - 1) * size;
        List<RssItemResponse> list = itemMapper.pageLatest(offset, size).stream()
                .map(RssItemResponse::from)
                .toList();
        return PageResult.of(list, itemMapper.countLatest(), page, size);
    }

    public List<RssFeedResponse> listFeeds() {
        Map<Long, Long> counts = countByFeed();
        return feedMapper.findAll().stream().map(f -> toResponse(f, counts)).toList();
    }

    /** 公开端订阅源列表：仅启用中的源 */
    public List<RssFeedResponse> listEnabledFeeds() {
        return feedMapper.findEnabled().stream()
                .map(f -> toResponse(f, Map.of()))
                .toList();
    }

    @Transactional
    public RssFeedResponse create(RssFeedRequest request) {
        checkUrls(request);
        if (feedMapper.countByFeedUrl(request.feedUrl()) > 0) {
            throw new BizException(ErrorCode.CONFLICT, "该订阅地址已存在");
        }
        RssFeed feed = new RssFeed();
        apply(feed, request);
        feed.setEnabled(request.enabledOrDefault());
        feedMapper.insert(feed);
        log.info("rss feed created: id={}, feedUrl={}", feed.getId(), feed.getFeedUrl());

        // 创建后立即首抓一次（尽力而为，失败不影响创建）
        fetchService.fetchOne(feedMapper.findById(feed.getId()));
        return toResponse(feedMapper.findById(feed.getId()), countByFeed());
    }

    @Transactional
    public RssFeedResponse update(Long id, RssFeedRequest request) {
        RssFeed exists = requireExists(id);
        checkUrls(request);
        long dup = feedMapper.countByFeedUrl(request.feedUrl());
        // 除自身外不允许重复的 feed_url
        boolean duplicated = dup > 1 || (dup == 1 && !exists.getFeedUrl().equals(request.feedUrl()));
        if (duplicated) {
            throw new BizException(ErrorCode.CONFLICT, "该订阅地址已存在");
        }
        apply(exists, request);
        feedMapper.update(exists);
        log.info("rss feed updated: id={}, feedUrl={}", id, exists.getFeedUrl());
        return toResponse(exists, countByFeed());
    }

    /** 手动触发单个源的抓取 */
    public void refresh(Long id) {
        RssFeed feed = requireExists(id);
        fetchService.fetchOne(feed);
    }

    @Transactional
    public void delete(Long id) {
        requireExists(id);
        itemMapper.deleteByFeedId(id); // 先删条目再删源，避免依赖数据库级联
        feedMapper.deleteById(id);
        log.info("rss feed deleted: id={}", id);
    }

    // ---------- internal ----------

    RssFeed requireExists(Long id) {
        RssFeed feed = feedMapper.findById(id);
        if (feed == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "订阅源不存在");
        }
        return feed;
    }

    private void checkUrls(RssFeedRequest request) {
        Strings.checkHttpUrl(request.siteUrl(), "站点地址");
        Strings.checkHttpUrl(request.feedUrl(), "订阅地址");
    }

    private void apply(RssFeed feed, RssFeedRequest request) {
        feed.setTitle(Strings.blankToNull(request.title()));
        feed.setSiteUrl(request.siteUrl());
        feed.setFeedUrl(request.feedUrl());
        feed.setDescription(Strings.blankToNull(request.description()));
        if (request.enabled() != null) {
            feed.setEnabled(request.enabledOrDefault());
        } else if (feed.getEnabled() == null) {
            feed.setEnabled(true);
        }
    }

    private RssFeedResponse toResponse(RssFeed feed, Map<Long, Long> counts) {
        return new RssFeedResponse(feed.getId(), feed.getTitle(), feed.getSiteUrl(),
                feed.getFeedUrl(), feed.getDescription(), Boolean.TRUE.equals(feed.getEnabled()),
                counts.getOrDefault(feed.getId(), 0L),
                feed.getLastFetchedAt(), feed.getLastError());
    }

    private Map<Long, Long> countByFeed() {
        Map<Long, Long> counts = new HashMap<>();
        for (RssFeed feed : feedMapper.findAll()) {
            counts.put(feed.getId(), itemMapper.countByFeedId(feed.getId()));
        }
        return counts;
    }

}

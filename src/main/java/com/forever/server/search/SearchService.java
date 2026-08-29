package com.forever.server.search;

import com.forever.server.article.Article;
import com.forever.server.article.ArticleMapper;
import com.forever.server.common.PageResult;
import com.forever.server.common.Strings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 全局搜索：MySQL LIKE 模糊匹配已发布文章的标题/摘要/正文（V14 索引表，见 SearchMapper），
 * 命中词带 HTML 高亮片段（转义后插 <em>，前端可安全 v-html）。
 */
@Slf4j
@Service
public class SearchService {

    private static final int MAX_KEYWORD_LENGTH = 100;
    /** 命中位置前后各保留的上下文字符数 */
    private static final int EXCERPT_CONTEXT = 60;
    private static final int EXCERPT_MAX_LENGTH = 120;
    private final SearchMapper searchMapper;
    private final ArticleMapper articleMapper;

    public SearchService(SearchMapper searchMapper, ArticleMapper articleMapper) {
        this.searchMapper = searchMapper;
        this.articleMapper = articleMapper;
    }

    /**
     * 全局搜索已发布文章。keyword 为空/全空白返回空页；超长截断。
     */
    public PageResult<SearchItemResponse> search(String keyword, int page, int size) {
        String kw = normalize(keyword);
        if (kw.isEmpty()) {
            return PageResult.of(List.of(), 0, page, size);
        }

        int offset = (page - 1) * size;
        List<SearchMapper.SearchRow> rows = searchMapper.page(kw, offset, size);
        long total = searchMapper.count(kw);
        if (rows.isEmpty()) {
            return PageResult.of(List.of(), total, page, size);
        }

        Map<Long, List<Article.TagItem>> tagsByArticle = articleMapper
                .tagRowsForArticles(rows.stream().map(SearchMapper.SearchRow::id).toList())
                .stream()
                .collect(Collectors.groupingBy(Article.TagItemRow::articleId,
                        Collectors.mapping(r -> new Article.TagItem(r.tagId(), r.tagName()),
                                Collectors.toList())));

        List<SearchItemResponse> items = rows.stream()
                .map(r -> new SearchItemResponse(r.id(), r.slug(), r.title(), r.categoryName(),
                        tagsByArticle.getOrDefault(r.id(), List.of()), r.createdAt(),
                        buildHighlights(r, kw)))
                .toList();
        // 搜索词是运营数据：记录访客在搜什么、命中率如何（个人博客流量小，info 一行开销可忽略）
        log.info("article searched: keyword=\"{}\", hits={}, page={}", kw, total, page);
        return PageResult.of(items, total, page, size);
    }

    // ---------- 高亮 ----------

    /**
     * 标题与摘要片段的高亮。先在原文里定位命中，再分段 HTML 转义后插入 <em>，
     * 保证转义结果里不会出现被拆散的关键词（前端 v-html 安全）。
     */
    SearchItemResponse.Highlights buildHighlights(SearchMapper.SearchRow row, String kw) {
        String plainContent = plainText(row.content());
        String summary = Strings.blankToNull(row.summary());

        // 片段来源：正文第一命中 > 摘要第一命中 > 纯文本开头兜底
        String excerpt;
        int idx = indexOfKeyword(plainContent, kw);
        if (idx >= 0) {
            excerpt = window(plainContent, kw, idx);
        } else if (summary != null && (idx = indexOfKeyword(summary, kw)) >= 0) {
            excerpt = window(summary, kw, idx);
        } else {
            String fallback = !plainContent.isBlank() ? plainContent
                    : (summary != null ? plainText(summary) : null);
            excerpt = fallback == null || fallback.isBlank() ? null
                    : HtmlUtils.htmlEscape(Strings.truncate(fallback, EXCERPT_MAX_LENGTH)) + (fallback.length() > EXCERPT_MAX_LENGTH ? "…" : "");
        }
        return new SearchItemResponse.Highlights(highlight(row.title(), kw), excerpt);
    }

    /** text 中第一处 kw（忽略大小写）包上 <em>；未命中原样转义返回 */
    private static String highlight(String text, String kw) {
        if (text == null) {
            return null;
        }
        int idx = indexOfKeyword(text, kw);
        if (idx < 0) {
            return HtmlUtils.htmlEscape(text);
        }
        return HtmlUtils.htmlEscape(text.substring(0, idx))
                + "<em>" + HtmlUtils.htmlEscape(text.substring(idx, idx + kw.length())) + "</em>"
                + HtmlUtils.htmlEscape(text.substring(idx + kw.length()));
    }

    /** 命中位置前后各留 EXCERPT_CONTEXT 字符的上下文片段，越界侧加省略号 */
    private static String window(String text, String kw, int idx) {
        int start = Math.max(0, idx - EXCERPT_CONTEXT);
        int end = Math.min(text.length(), idx + kw.length() + EXCERPT_CONTEXT);
        return (start > 0 ? "…" : "") + highlight(text.substring(start, end), kw)
                + (end < text.length() ? "…" : "");
    }

    /**
     * Markdown 纯文本化：去掉代码块/图片/链接语法与行内标记，保留可读文字。
     * ponytail: 正则近似去语法，只为搜索摘要服务；要精确渲染请走前端 Markdown 管线。
     */
    private static String plainText(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        return markdown
                .replaceAll("```[\\s\\S]*?```", " ")
                .replaceAll("!\\[[^\\]]*]\\([^)]*\\)", " ")
                .replaceAll("\\[([^\\]]*)]\\([^)]*\\)", "$1")
                .replaceAll("(?m)^\\s{0,3}#{1,6}\\s+|^\\s{0,3}[>*-]\\s+", "")
                .replaceAll("[*_`~]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static int indexOfKeyword(String text, String kw) {
        if (text == null || text.isEmpty()) {
            return -1;
        }
        return text.toLowerCase().indexOf(kw.toLowerCase());
    }

    private static String normalize(String keyword) {
        if (keyword == null) {
            return "";
        }
        String kw = keyword.trim();
        return kw.length() > MAX_KEYWORD_LENGTH ? kw.substring(0, MAX_KEYWORD_LENGTH) : kw;
    }
}

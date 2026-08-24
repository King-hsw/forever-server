package com.forever.server.search;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 高亮片段逻辑自检：HTML 转义、<em> 包裹、Markdown 纯文本化、上下文窗口 */
class SearchServiceTest {

    private final SearchService service = new SearchService(null, null);

    private SearchItemResponse.Highlights highlights(String title, String summary, String content, String kw) {
        return service.buildHighlights(new SearchMapper.SearchRow(
                1L, "slug", title, summary, content, null, LocalDateTime.now()), kw);
    }

    @Test
    void 标题命中包裹em且转义其余HTML() {
        var h = highlights("Java <b>入门</b>指南", null, "正文", "入门");
        assertEquals("Java &lt;b&gt;<em>入门</em>&lt;/b&gt;指南", h.title());
    }

    @Test
    void 正文命中截取上下文并加省略号() {
        String filler = "字".repeat(100);
        var h = highlights("标题", null, filler + "关键词在这里" + filler, "关键词");
        assertTrue(h.excerpt().startsWith("…"));
        assertTrue(h.excerpt().endsWith("…"));
        assertTrue(h.excerpt().contains("<em>关键词</em>"));
        // 命中前后各约 60 字符
        assertFalse(h.excerpt().length() > 200);
    }

    @Test
    void xss内容先转义再包em() {
        var h = highlights("标题", null, "前文 <script>alert(1)</script> 关键词 后文", "关键词");
        assertFalse(h.excerpt().contains("<script>"));
        assertTrue(h.excerpt().contains("&lt;script&gt;"));
        assertTrue(h.excerpt().contains("<em>关键词</em>"));
    }

    @Test
    void 仅标题命中时摘要取正文开头无高亮() {
        var h = highlights("标题", "# 简介第一行\n[链接文字](https://x.com)", "正文没有命中词", "标题");
        assertEquals("正文没有命中词", h.excerpt());
    }

    @Test
    void 正文为空时兑底用简介且去Markdown语法() {
        var h = highlights("标题", "# 简介第一行\n[链接文字](https://x.com)", null, "标题");
        assertEquals("简介第一行 链接文字", h.excerpt());
    }

    @Test
    void 大小写不敏感命中() {
        var h = highlights("How to Use SPRING boot", null, "content without hit", "boot");
        assertTrue(h.title().contains("<em>boot</em>"));
    }
}

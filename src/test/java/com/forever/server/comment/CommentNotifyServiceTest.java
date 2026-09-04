package com.forever.server.comment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommentNotifyServiceTest {

    private static final String SITE_URL = "https://blog.hongsw.cn";

    // ---------- 主题三分支 ----------

    @Test
    void newCommentSubject_threeBranches() {
        assertEquals("《我的文章》收到新评论",
                CommentNotifyService.newCommentSubject(CommentService.TARGET_ARTICLE, "我的文章"));
        assertEquals("留言板收到新留言",
                CommentNotifyService.newCommentSubject(CommentService.TARGET_BOARD, "留言板"));
        assertEquals("动态收到新评论",
                CommentNotifyService.newCommentSubject(CommentService.TARGET_MOMENT, "今天去了趟海边…"));
    }

    @Test
    void replySubject_threeBranches() {
        assertEquals("你的评论收到了新回复", CommentNotifyService.replySubject(CommentService.TARGET_ARTICLE));
        assertEquals("你的留言收到了新回复", CommentNotifyService.replySubject(CommentService.TARGET_BOARD));
        assertEquals("你的动态评论收到了新回复", CommentNotifyService.replySubject(CommentService.TARGET_MOMENT));
    }

    // ---------- 模板输出 ----------

    @Test
    void newCommentHtml_withSiteUrl_rendersLinksAndEscapesUserInput() {
        String html = CommentNotifyService.buildNewCommentHtml(
                "小辉的博客", "某文章标题", "/posts/demo", "https://blog.hongsw.cn/",
                "张三<script>", "内容 & 更多 <b>加粗</b>");
        // 来源标题与「查看」均渲染为绝对链接（site.url 去尾部斜杠）
        assertTrue(html.contains("<a href=\"https://blog.hongsw.cn/posts/demo\" style=\"color:#0d9488;text-decoration:none;\">《某文章标题》</a>"));
        assertTrue(html.contains("<a href=\"https://blog.hongsw.cn/posts/demo\" style=\"color:#0d9488;\">查看</a>"));
        // 用户输入被转义，无未转义残留
        assertTrue(html.contains("张三&lt;script&gt;"));
        assertTrue(html.contains("内容 &amp; 更多 &lt;b&gt;加粗&lt;/b&gt;"));
        assertFalse(html.contains("<script>"));
        assertFalse(html.contains("<b>加粗</b>"));
        // 站点头部
        assertTrue(html.contains("小辉的博客"));
    }

    @Test
    void newCommentHtml_siteUrlMissing_degradesToPlainTextNoLinks() {
        for (String siteUrl : new String[]{null, "  "}) {
            String html = CommentNotifyService.buildNewCommentHtml(
                    "小辉的博客", "某文章标题", "/posts/demo", siteUrl,
                    "张三", "评论内容");
            assertFalse(html.contains("<a"), "site.url 缺失时不应渲染任何链接: " + html);
            // 正文仍是完整纯文本描述
            assertTrue(html.contains("《某文章标题》"));
            assertTrue(html.contains("张三："));
            assertTrue(html.contains("评论内容"));
        }
    }

    @Test
    void replyHtml_twoQuoteBlocksEscaped() {
        String html = CommentNotifyService.buildReplyHtml(
                "小辉的博客", "某文章标题", "/posts/demo", SITE_URL,
                "老张", "原评论内容", "小李", "新回复 <img> 内容");
        assertTrue(html.contains("老张："));
        assertTrue(html.contains("原评论内容"));
        assertTrue(html.contains("小李："));
        assertTrue(html.contains("新回复 &lt;img&gt; 内容"));
        // 原评论 + 新回复两个引用块
        assertEquals(2, html.split("border-left:3px solid #0d9488", -1).length - 1);
        assertTrue(html.contains("<a href=\"https://blog.hongsw.cn/posts/demo\" style=\"color:#0d9488;\">查看</a>"));
    }
}

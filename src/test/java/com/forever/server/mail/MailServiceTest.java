package com.forever.server.mail;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MailServiceTest {

    @Test
    void escapeHtml_ampFirst() {
        // & 必须最先转义：已有实体不得被二次转义
        assertEquals("a &amp; b", MailService.escapeHtml("a & b"));
        assertEquals("&amp;amp;lt;", MailService.escapeHtml("&amp;lt;"));
    }

    @Test
    void escapeHtml_mixed() {
        assertEquals("&lt;a href=&quot;x&quot;&gt;&amp;&lt;/a&gt;",
                MailService.escapeHtml("<a href=\"x\">&</a>"));
        assertEquals("《安全》&lt;script&gt;alert(1)&lt;/script&gt;",
                MailService.escapeHtml("《安全》<script>alert(1)</script>"));
    }

    @Test
    void escapeHtml_nullAndPlain() {
        assertEquals("", MailService.escapeHtml(null));
        assertEquals("hello 世界 123", MailService.escapeHtml("hello 世界 123"));
    }
}

package com.forever.server.article;

/**
 * 正文的存储格式。Core 只负责存取，渲染由客户端按格式自行处理；
 * 未来新增 MDX / RICH_TEXT 等格式时 Core 无需改动。
 */
public enum ContentFormat {
    /** Markdown 原文（当前默认） */
    MARKDOWN,
    /** 富文本 / HTML */
    HTML
}

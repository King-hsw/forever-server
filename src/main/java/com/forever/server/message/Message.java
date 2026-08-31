package com.forever.server.message;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内消息（单向通知）：收件人即账号 uid，生成后不可编辑。
 */
@Data
public class Message {

    private Long id;
    /** 收件人 sys_user.id */
    private Long userId;
    /** COMMENT_REPLY / NEW_COMMENT */
    private String type;
    /** 摘要文案 */
    private String content;
    /** 点击跳转链接（含锚点） */
    private String sourceUrl;
    private Boolean isRead;
    private Boolean deleted;
    private LocalDateTime createdAt;
}

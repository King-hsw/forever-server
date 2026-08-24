package com.forever.server.friendlink;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 友情链接。
 * contact 与 reject_reason 仅管理端可见，前台响应中不返回。
 */
@Data
public class FriendLink {

    private Long id;
    private String name;
    private String siteUrl;
    private String iconUrl;
    private String description;
    private String contact;
    private FriendLinkStatus status;
    private String rejectReason;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
}

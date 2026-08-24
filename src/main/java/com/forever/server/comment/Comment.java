package com.forever.server.comment;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 访客评论。两层楼结构：
 * 根评论 parent_id = null；回复 parent_id 指向被回复评论，root_id 统一指向所属根评论。
 */
@Data
public class Comment {

    private Long id;
    /** 归属对象：ARTICLE-文章 id / BOARD-固定 0 */
    private String targetType;
    private Long targetId;
    private Long parentId;
    private Long rootId;
    private String nickname;
    /** 不对外展示，仅用于头像生成与回复邮件通知 */
    private String email;
    private String site;
    private String content;
    /** APPROVED / PENDING / REJECTED */
    private String status;
    private String ip;
    private LocalDateTime createdAt;
}

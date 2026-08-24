package com.forever.server.comment;

import java.time.LocalDateTime;

/**
 * 访客评论。两层楼结构：
 * 根评论 parent_id = null；回复 parent_id 指向被回复评论，root_id 统一指向所属根评论。
 */
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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public void setTargetId(Long targetId) {
        this.targetId = targetId;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public Long getRootId() {
        return rootId;
    }

    public void setRootId(Long rootId) {
        this.rootId = rootId;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getSite() {
        return site;
    }

    public void setSite(String site) {
        this.site = site;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

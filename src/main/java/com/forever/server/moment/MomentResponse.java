package com.forever.server.moment;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "动态视图")
public record MomentResponse(
        Long id,
        Long uid,
        @Schema(description = "发布者账号名") String username,
        @Schema(description = "自定义头像 URL；null 时前端按惯例回退 Gravatar/首字") String avatarUrl,
        String content,
        MomentMedia media,
        @Schema(description = "地点文本") String location,
        @Schema(description = "纬度，可为 null") Double lat,
        @Schema(description = "经度，可为 null") Double lng,
        @Schema(description = "发布时间，ISO 8601") LocalDateTime createdAt,
        long likeCount,
        @Schema(description = "当前访问者（登录时）是否已赞；匿名恒 false") boolean liked,
        @Schema(description = "已过审评论数") long commentCount,
        @Schema(description = "作者或 ADMIN 角色可删；匿名恒 false") boolean canDelete) {
}

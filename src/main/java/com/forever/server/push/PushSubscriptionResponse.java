package com.forever.server.push;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 管理端订阅列表项：endpoint 脱敏只留尾段（完整 endpoint 含 token，无展示必要）
 */
@Schema(description = "推送订阅列表项")
public record PushSubscriptionResponse(
        @Schema(description = "订阅地址尾段（脱敏）", example = "…4dYXA2lx0ZQyAiA")
        String endpointTail,
        @Schema(description = "绑定的登录用户，游客订阅为 null") Long userId,
        @Schema(description = "首次订阅时间") LocalDateTime addedAt,
        @Schema(description = "最近一次推送成功时间") LocalDateTime lastSentAt,
        @Schema(description = "最近一次送达回执时间") LocalDateTime lastDeliveredAt) {

    public static PushSubscriptionResponse from(PushSubscription sub) {
        String endpoint = sub.getEndpoint();
        return new PushSubscriptionResponse(
                endpoint.length() <= 24 ? endpoint : endpoint.substring(endpoint.length() - 24),
                sub.getUserId(), sub.getAddedAt(), sub.getLastSentAt(), sub.getLastDeliveredAt());
    }
}

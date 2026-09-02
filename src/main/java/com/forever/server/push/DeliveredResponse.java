package com.forever.server.push;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 送达概况：SW 收到 push 后回执，按订阅行统计
 */
@Schema(description = "推送送达概况")
public record DeliveredResponse(
        @Schema(description = "已确认送达的订阅数") long count,
        @Schema(description = "最近一次回执时间") LocalDateTime lastAt) {
}

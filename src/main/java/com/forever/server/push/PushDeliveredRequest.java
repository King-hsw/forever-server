package com.forever.server.push;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 送达回执请求：Service Worker 收到 push 事件后上报，携带自身订阅的 endpoint
 */
@Schema(description = "推送送达回执请求")
public record PushDeliveredRequest(
        @Schema(description = "回执方订阅的推送地址")
        @NotBlank(message = "endpoint 不能为空") @Size(max = 1000) String endpoint) {
}

package com.forever.server.push;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 退订请求：按 endpoint 删除服务端记录 */
@Schema(description = "Web Push 退订请求")
public record PushUnsubscribeRequest(
        @Schema(description = "推送服务分配的订阅地址")
        @NotBlank(message = "endpoint 不能为空") String endpoint) {
}

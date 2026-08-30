package com.forever.server.push;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 推送订阅请求：前端 pushManager.subscribe 结果的 JSON（多余字段如 expirationTime 被忽略） */
@Schema(description = "Web Push 订阅请求")
public record PushSubscribeRequest(
        @Schema(description = "推送服务分配的订阅地址", example = "https://fcm.googleapis.com/fcm/send/…")
        @NotBlank(message = "endpoint 不能为空") @Size(max = 1000) String endpoint,
        @Schema(description = "载荷加密密钥对")
        @NotBlank Keys keys) {

    @Schema(description = "载荷加密密钥对")
    public record Keys(
            @Schema(description = "客户端 ECDH 公钥（Base64URL）")
            @NotBlank(message = "keys.p256dh 不能为空") @Size(max = 200) String p256dh,
            @Schema(description = "鉴权密钥（Base64URL）")
            @NotBlank(message = "keys.auth 不能为空") @Size(max = 200) String auth) {
    }
}

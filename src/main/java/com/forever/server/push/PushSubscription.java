package com.forever.server.push;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Web Push 浏览器订阅记录（前端 pushManager.subscribe 的结果）。
 */
@Data
public class PushSubscription {

    private Long id;
    /** 推送服务分配的订阅地址（FCM/Mozilla autopush 等） */
    private String endpoint;
    /** 客户端 ECDH 公钥（Base64URL），载荷加密用 */
    private String p256dh;
    /** 鉴权密钥（Base64URL），载荷加密用 */
    private String auth;
    /** 绑定的登录用户；游客订阅为 null */
    private Long userId;
    /** 归属邮箱：登录用户取 sys_user.email，游客在评论成功后上报；定向推送（评论回复）按此命中 */
    private String email;
    private LocalDateTime addedAt;
    private LocalDateTime lastSentAt;
    private LocalDateTime lastDeliveredAt;
}

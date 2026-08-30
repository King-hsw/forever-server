package com.forever.server.push;

import com.forever.server.auth.AuthPrincipal;
import com.forever.server.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 前台推送接口（公开）：订阅、退订、VAPID 公钥下发与送达回执。
 * 订阅结果按 endpoint 幂等；带登录态调用时把订阅绑定到当前用户，游客订阅归属为空。
 */
@Tag(name = "推送（前台）", description = "Web Push 订阅与送达回执，公开接口")
@RestController
@RequestMapping("/api/v1/push")
public class PushController {

    private final PushService service;

    public PushController(PushService service) {
        this.service = service;
    }

    @Operation(summary = "VAPID 公钥", description = "前端 pushManager.subscribe 的 applicationServerKey；推送未配置时报 503")
    @GetMapping("/vapid")
    public ApiResponse<Map<String, String>> vapid() {
        return ApiResponse.ok(Map.of("publicKey", service.publicKey()));
    }

    @Operation(summary = "保存推送订阅", description = "按 endpoint 幂等 upsert，浏览器重新生成密钥时以新值覆盖")
    @PostMapping("/subscribe")
    public ApiResponse<Void> subscribe(@Valid @RequestBody PushSubscribeRequest request) {
        service.subscribe(request.endpoint(), request.keys().p256dh(), request.keys().auth(), currentUid());
        return ApiResponse.ok();
    }

    @Operation(summary = "取消订阅", description = "按 endpoint 删除服务端记录；记录不存在时同样成功（幂等）")
    @PostMapping("/unsubscribe")
    public ApiResponse<Void> unsubscribe(@Valid @RequestBody PushUnsubscribeRequest request) {
        service.unsubscribe(request.endpoint());
        return ApiResponse.ok();
    }

    @Operation(summary = "送达回执", description = "Service Worker 收到 push 事件即上报，更新该订阅的回执时间")
    @PostMapping("/delivered")
    public ApiResponse<Void> delivered(@Valid @RequestBody PushDeliveredRequest request) {
        service.markDelivered(request.endpoint());
        return ApiResponse.ok();
    }

    @Operation(summary = "送达概况", description = "已确认送达的订阅数与最近一次回执时间（页面轮询展示）")
    @GetMapping("/delivered")
    public ApiResponse<DeliveredResponse> deliveredSummary() {
        return ApiResponse.ok(service.deliveredSummary());
    }

    /** 带登录态订阅时返回当前用户 id，游客返回 null */
    private static Long currentUid() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AuthPrincipal principal ? principal.uid() : null;
    }
}

package com.forever.server.push;

import com.forever.server.auth.Perm;
import com.forever.server.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端推送接口：查看订阅与测试广播。业务事件（新评论等）触发的定向推送由服务内部调用，不经 HTTP。
 */
@Tag(name = "推送管理", description = "Web Push 订阅管理与测试发送，需 JWT 认证")
@RestController
@RequestMapping("/api/admin/push")
@RequiredArgsConstructor
public class AdminPushController {

    private final PushService service;

    @Perm("push:list")
    @Operation(summary = "订阅列表", description = "全量返回，endpoint 脱敏只留尾段，附归属用户与推送/送达时间")
    @GetMapping("/subscriptions")
    public ApiResponse<List<PushSubscriptionResponse>> subscriptions() {
        return ApiResponse.ok(service.listSubscriptions().stream().map(PushSubscriptionResponse::from).toList());
    }

    @Perm("push:send")
    @Operation(summary = "发送测试推送", description = "向全部订阅广播一条推送并返回成功/失败统计；404/410 的失效订阅会被顺带清理")
    @PostMapping("/send")
    public ApiResponse<PushSendResult> send(@Valid @RequestBody PushSendRequest request) {
        // 点击跳转只允许站内路径，防开放跳转；非法值置空由前端 SW 兜底跳 /
        String url = request.url() != null && request.url().startsWith("/") ? request.url() : null;
        return ApiResponse.ok(service.sendAll(request.title(), request.body(), url));
    }
}

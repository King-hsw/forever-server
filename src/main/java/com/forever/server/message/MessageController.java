package com.forever.server.message;

import com.forever.server.auth.AuthPrincipal;
import com.forever.server.common.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 消息中心：登录账号的站内消息收件箱。
 * 挂在 /api/v1/** 下（默认放行），各端点自行校验登录态。
 */
@Tag(name = "消息中心", description = "站内消息收件箱，登录后可见")
@RestController
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @Operation(summary = "分页查看消息", description = "created_at 倒序")
    @GetMapping("/api/v1/messages")
    public ApiResponse<PageResult<MessageDtos.MessageResponse>> list(
            @Parameter(description = "页码，从 1 开始", example = "1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数（最大 100）", example = "20") @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        long uid = requireUid(authentication);
        page = PageParams.normalizePage(page);
        size = PageParams.normalizeSize(size);
        return ApiResponse.ok(messageService.page(uid, page, size));
    }

    @Operation(summary = "未读消息数")
    @GetMapping("/api/v1/messages/unread-count")
    public ApiResponse<MessageDtos.UnreadCountResponse> unreadCount(Authentication authentication) {
        return ApiResponse.ok(new MessageDtos.UnreadCountResponse(
                messageService.unreadCount(requireUid(authentication))));
    }

    @Operation(summary = "标记已读")
    @PutMapping("/api/v1/messages/{id}/read")
    public ApiResponse<Void> read(@PathVariable Long id, Authentication authentication) {
        messageService.markRead(requireUid(authentication), id);
        return ApiResponse.ok();
    }

    @Operation(summary = "全部已读")
    @PutMapping("/api/v1/messages/read-all")
    public ApiResponse<Void> readAll(Authentication authentication) {
        messageService.markAllRead(requireUid(authentication));
        return ApiResponse.ok();
    }

    @Operation(summary = "删除单条消息")
    @DeleteMapping("/api/v1/messages/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, Authentication authentication) {
        messageService.delete(requireUid(authentication), id);
        return ApiResponse.ok();
    }

    /**
     * 强制登录：/api/v1/** 默认放行，匿名 / 无效凭证统一 401
     */
    private static long requireUid(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthPrincipal p) {
            return p.uid();
        }
        throw new BizException(ErrorCode.UNAUTHORIZED);
    }
}

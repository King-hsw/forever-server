package com.forever.server.friendlink;

import com.forever.server.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "友链·公开接口", description = "前台展示与访客申请接口，无需认证")
@RestController
@RequestMapping("/api/v1/friend-links")
@RequiredArgsConstructor
public class PublicFriendLinkController {

    private final FriendLinkService service;

    @Operation(summary = "友链列表", description = "仅返回审核通过的友链，供前台展示")
    @GetMapping
    public ApiResponse<List<FriendLinkResponse>> list() {
        return ApiResponse.ok(service.listApproved());
    }

    @Operation(summary = "提交友链申请", description = "站点地址不可重复；创建后为 PENDING 状态，待管理员审核")
    @PostMapping("/apply")
    public ApiResponse<FriendLinkResponse> apply(@Valid @RequestBody FriendLinkApplyRequest request) {
        return ApiResponse.ok(service.apply(request));
    }
}

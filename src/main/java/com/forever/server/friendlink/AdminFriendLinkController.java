package com.forever.server.friendlink;

import com.forever.server.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "友链管理", description = "管理端友链维护接口，需 JWT 认证")
@RestController
@RequestMapping("/api/admin/friend-links")
public class AdminFriendLinkController {

    private final FriendLinkService service;

    public AdminFriendLinkController(FriendLinkService service) {
        this.service = service;
    }

    @Operation(summary = "友链列表", description = "全量返回（含待审核、已驳回），附联系方式与驳回原因")
    @GetMapping
    public ApiResponse<List<FriendLinkResponse>> list() {
        return ApiResponse.ok(service.listAll());
    }

    @Operation(summary = "创建友链", description = "管理端直接录入友链，无需审核，创建即在前台展示")
    @PostMapping
    public ApiResponse<FriendLinkResponse> create(@Valid @RequestBody FriendLinkApplyRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @Operation(summary = "更新友链", description = "全量更新：未传的字段会被置空")
    @PutMapping("/{id}")
    public ApiResponse<FriendLinkResponse> update(@PathVariable Long id,
                                                  @Valid @RequestBody FriendLinkUpdateRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @Operation(summary = "通过审核", description = "状态置为 APPROVED，通过后在前台展示")
    @PostMapping("/{id}/approve")
    public ApiResponse<FriendLinkResponse> approve(@PathVariable Long id) {
        return ApiResponse.ok(service.approve(id));
    }

    @Operation(summary = "驳回申请", description = "状态置为 REJECTED，可附带驳回原因（仅管理端可见）")
    @PostMapping("/{id}/reject")
    public ApiResponse<FriendLinkResponse> reject(@PathVariable Long id,
                                                  @RequestParam(required = false) String reason) {
        return ApiResponse.ok(service.reject(id, reason));
    }

    @Operation(summary = "删除友链")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}

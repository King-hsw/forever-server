package com.forever.server.friendlink;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 友链全量更新请求（管理端）
 */
@Schema(description = "友链更新请求（全量提交）")
public record FriendLinkUpdateRequest(
        @Schema(description = "站点名称")
        @NotBlank(message = "站点名称不能为空") @Size(max = 100) String name,
        @Schema(description = "站点地址")
        @NotBlank(message = "站点地址不能为空") @Size(max = 500) String siteUrl,
        @Schema(description = "站点图标/头像地址，传空则清除")
        @Size(max = 500) String iconUrl,
        @Schema(description = "一句话简介，传空则清除")
        @Size(max = 200) String description,
        @Schema(description = "状态：PENDING / APPROVED / REJECTED")
        @NotNull(message = "状态不能为空") FriendLinkStatus status,
        @Schema(description = "驳回原因，仅状态为 REJECTED 时有意义")
        @Size(max = 200) String rejectReason) {
}

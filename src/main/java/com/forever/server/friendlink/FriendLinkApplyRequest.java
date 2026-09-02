package com.forever.server.friendlink;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 友链申请请求（访客提交）
 */
@Schema(description = "友链申请请求")
public record FriendLinkApplyRequest(
        @Schema(description = "站点名称", example = "张三的小站")
        @NotBlank(message = "站点名称不能为空") @Size(max = 100) String name,
        @Schema(description = "站点地址", example = "https://example.com")
        @NotBlank(message = "站点地址不能为空") @Size(max = 500) String siteUrl,
        @Schema(description = "站点图标/头像地址，可不填", example = "https://example.com/avatar.png")
        @Size(max = 500) String iconUrl,
        @Schema(description = "一句话简介，可不填", example = "记录生活与技术")
        @Size(max = 200) String description,
        @Schema(description = "联系方式（邮箱等），便于反馈审核结果", example = "me@example.com")
        @Size(max = 200) String contact) {
}

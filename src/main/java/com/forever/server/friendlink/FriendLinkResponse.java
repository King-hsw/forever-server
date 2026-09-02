package com.forever.server.friendlink;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "友链信息；公开接口不返回 contact 与 rejectReason")
public record FriendLinkResponse(
        Long id,
        @Schema(description = "站点名称") String name,
        @Schema(description = "站点地址") String siteUrl,
        @Schema(description = "站点图标/头像") String iconUrl,
        @Schema(description = "一句话简介") String description,
        @Schema(description = "状态：PENDING / APPROVED / REJECTED") FriendLinkStatus status,
        @Schema(description = "申请时间") LocalDateTime createdAt,
        @Schema(description = "审核时间；未审核为 null") LocalDateTime reviewedAt,
        @Schema(description = "申请者联系方式，仅管理端可见") String contact,
        @Schema(description = "驳回原因，仅管理端可见") String rejectReason) {

    /**
     * 公开视图：隐藏联系方式与驳回原因
     */
    public static FriendLinkResponse publicView(FriendLink link) {
        return new FriendLinkResponse(link.getId(), link.getName(), link.getSiteUrl(),
                link.getIconUrl(), link.getDescription(), link.getStatus(),
                link.getCreatedAt(), link.getReviewedAt(), null, null);
    }

    /**
     * 管理视图：包含全部字段
     */
    public static FriendLinkResponse adminView(FriendLink link) {
        return new FriendLinkResponse(link.getId(), link.getName(), link.getSiteUrl(),
                link.getIconUrl(), link.getDescription(), link.getStatus(),
                link.getCreatedAt(), link.getReviewedAt(), link.getContact(), link.getRejectReason());
    }
}

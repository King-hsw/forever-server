package com.forever.server.moment;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 动态模块小 DTO 集合。
 */
public final class MomentDtos {

    private MomentDtos() {
    }

    @Schema(description = "点赞操作结果")
    public record LikeResponse(
            long likeCount,
            boolean liked) {
    }

    @Schema(description = "逆地理编码结果；text 为 null 表示未配置 key 或调用失败")
    public record GeocodeResponse(
            @Schema(description = "地点文本，如「北京市朝阳区」") String text) {
    }
}

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

    @Schema(description = "媒体上传结果")
    public record UploadResponse(
            @Schema(description = "访问地址，/uploads/moment/{yyyy/MM}/{uuid}.{ext}") String url) {
    }

    @Schema(description = "直传预签申请")
    public record PresignUploadRequest(
            @Schema(description = "文件 MIME 类型，须在白名单内", example = "image/png") String contentType) {
    }

    @Schema(description = "直传预签结果")
    public record PresignUploadResponse(
            @Schema(description = "限时直传 PUT 地址；PUT 时须携带与 contentType 一致的请求头") String url,
            @Schema(description = "直传对象 key（tmp/ 前缀），发布动态时回传到 images / audio / video 字段") String key,
            @Schema(description = "PUT 时须携带的 Content-Type 值") String contentType,
            @Schema(description = "签名有效期（秒）") long expiresIn) {
    }

    @Schema(description = "逆地理编码结果；text 为 null 表示未配置 key 或调用失败")
    public record GeocodeResponse(
            @Schema(description = "地点文本，如「北京市朝阳区」") String text) {
    }
}

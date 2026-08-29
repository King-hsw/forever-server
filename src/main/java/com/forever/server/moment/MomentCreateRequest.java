package com.forever.server.moment;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "发布动态请求")
public record MomentCreateRequest(
        @Schema(description = "文本内容，最多 1000 字", example = "今日份摸鱼")
        @Size(max = 1000, message = "内容最多 1000 字") String content,
        @Schema(description = "图片 URL 列表，最多 9 张；元素为 /uploads/moment/... 正式地址或直传返回的 tmp/ 开头 key（发布时自动收口）")
        @Size(max = 9, message = "图片最多 9 张") List<String> images,
        @Schema(description = "音频 URL 或直传 key，最多 1 个") String audio,
        @Schema(description = "视频 URL 或直传 key，最多 1 个") String video,
        @Schema(description = "地点文本，最多 100 字")
        @Size(max = 100, message = "地点最多 100 字") String location,
        @Schema(description = "纬度") Double lat,
        @Schema(description = "经度") Double lng) {
}

package com.forever.server.moment;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "动态媒体：图片最多 9 张，音频/视频各最多 1 个")
public record MomentMedia(
        @Schema(description = "图片 URL 列表，无图片时为空数组") List<String> images,
        @Schema(description = "音频 URL，可为 null") String audio,
        @Schema(description = "视频 URL，可为 null") String video) {

    public MomentMedia {
        images = images == null ? List.of() : List.copyOf(images);
    }

    public boolean isEmpty() {
        return images.isEmpty() && audio == null && video == null;
    }
}

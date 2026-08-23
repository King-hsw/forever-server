package com.forever.server.tag;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "标签信息")
public record TagResponse(
        @Schema(description = "标签 id", example = "1") Long id,
        @Schema(description = "标签名称") String name,
        @Schema(description = "关联的已发布文章数") long articleCount) {
}

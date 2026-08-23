package com.forever.server.category;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "分类信息")
public record CategoryResponse(
        @Schema(description = "分类 id", example = "1") Long id,
        @Schema(description = "分类名称") String name,
        @Schema(description = "URL 别名") String slug,
        @Schema(description = "排序值") int sort,
        @Schema(description = "已发布文章数") long articleCount) {
}

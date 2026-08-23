package com.forever.server.category;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 分类创建/更新请求 */
@Schema(description = "分类创建/更新请求")
public record CategoryRequest(
        @Schema(description = "分类名称", example = "技术分享")
        @NotBlank(message = "分类名称不能为空") @Size(max = 50) String name,
        @Schema(description = "URL 别名；可不填，服务端自动生成", example = "tech")
        @Size(max = 100) String slug,
        @Schema(description = "排序值，越小越靠前", example = "0")
        Integer sort) {

    public int sortOrDefault() {
        return sort == null ? 0 : sort;
    }
}

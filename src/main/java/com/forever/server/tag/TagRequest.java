package com.forever.server.tag;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 标签创建/更新请求
 */
@Schema(description = "标签创建/更新请求")
public record TagRequest(
        @Schema(description = "标签名称", example = "Java")
        @NotBlank(message = "标签名称不能为空") @Size(max = 50) String name) {
}

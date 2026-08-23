package com.forever.server.sensitive;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "敏感词创建/更新请求")
public record SensitiveWordRequest(
        @Schema(description = "敏感词", example = "示例词")
        @NotBlank(message = "敏感词不能为空") @Size(max = 100) String word,
        @Schema(description = "命中后的替换文本，不填默认 ***", example = "***")
        @Size(max = 100) String replacement) {
}

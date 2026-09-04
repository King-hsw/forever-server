package com.forever.server.setting;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 站点设置 DTO。
 */
public final class SettingDtos {

    private SettingDtos() {
    }

    @Schema(description = "站点配置项")
    public record SettingResponse(
            @Schema(description = "配置键", example = "comment.post-interval-seconds") String key,
            @Schema(description = "当前生效值；空表示未在数据库中设置，走 yml 默认值", example = "10") String value,
            @Schema(description = "配置说明") String description) {
    }

    @Schema(description = "修改站点配置请求")
    public record SettingUpdateRequest(
            @Schema(description = "配置键", example = "comment.post-interval-seconds")
            @NotBlank(message = "key 不能为空")
            String key,
            @Schema(description = "配置值（数值型配置须为 >= 0 的整数）；留空表示清除配置、恢复默认值", example = "15")
            @NotNull(message = "value 不能为 null")
            String value) {
    }
}

package com.forever.server.rss;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 订阅源创建/更新请求
 */
@Schema(description = "RSS 订阅源创建/更新请求")
public record RssFeedRequest(
        @Schema(description = "站点名称；不填则抓取成功后自动取 feed 自带标题", example = "阮一峰的网络日志")
        @Size(max = 200) String title,
        @Schema(description = "博客主页地址", example = "https://www.ruanyifeng.com/blog/")
        @NotBlank(message = "站点地址不能为空") @Size(max = 500) String siteUrl,
        @Schema(description = "RSS/Atom 订阅地址", example = "https://www.ruanyifeng.com/blog/atom.xml")
        @NotBlank(message = "订阅地址不能为空") @Size(max = 500) String feedUrl,
        @Schema(description = "备注描述，可不填") @Size(max = 500) String description,
        @Schema(description = "是否启用抓取，默认 true") Boolean enabled) {

    public boolean enabledOrDefault() {
        return enabled == null || enabled;
    }
}

package com.forever.server.push;

import io.swagger.v3.oas.annotations.media.Schema;

/** 发送推送请求：字段均可选，缺省用默认值；url 仅接受站内路径（/ 开头） */
@Schema(description = "发送推送请求")
public record PushSendRequest(
        @Schema(description = "通知标题，缺省为站点名", example = "补陋阁")
        String title,
        @Schema(description = "通知正文，缺省为「你有新消息」", example = "你有新评论待审")
        String body,
        @Schema(description = "点击通知跳转的站内路径，缺省 /", example = "/push-demo")
        String url) {
}

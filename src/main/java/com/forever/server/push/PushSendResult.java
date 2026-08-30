package com.forever.server.push;

import io.swagger.v3.oas.annotations.media.Schema;

/** 广播发送结果统计 */
@Schema(description = "推送发送结果")
public record PushSendResult(
        @Schema(description = "本次尝试的订阅总数") long total,
        @Schema(description = "成功条数") int sent,
        @Schema(description = "失败条数（含已失效被清理的）") int failed) {
}

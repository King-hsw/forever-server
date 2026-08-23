package com.forever.server.actionlog;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "审计日志条目")
public record ActionLogResponse(
        @Schema(description = "id") Long id,
        @Schema(description = "操作人；匿名请求为 null", example = "admin") String username,
        @Schema(description = "HTTP 方法", example = "POST") String method,
        @Schema(description = "请求路径", example = "/api/admin/articles/1/publish") String path,
        @Schema(description = "HTTP 响应码", example = "200") Integer status,
        @Schema(description = "来源 IP") String ip,
        @Schema(description = "耗时（毫秒）", example = "35") Long durationMs,
        @Schema(description = "时间") LocalDateTime createdAt) {

    static ActionLogResponse from(ActionLog log) {
        return new ActionLogResponse(log.getId(), log.getUsername(), log.getMethod(),
                log.getPath(), log.getStatus(), log.getIp(), log.getDurationMs(), log.getCreatedAt());
    }
}

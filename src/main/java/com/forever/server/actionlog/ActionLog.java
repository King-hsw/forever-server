package com.forever.server.actionlog;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志：一条后台写操作或一次登录尝试
 */
@Data
public class ActionLog {

    private Long id;
    private String username;
    private String method;
    private String path;
    /**
     * HTTP 响应码
     */
    private Integer status;
    private String ip;
    private Long durationMs;
    private LocalDateTime createdAt;
}

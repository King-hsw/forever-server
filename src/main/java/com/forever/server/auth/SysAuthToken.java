package com.forever.server.auth;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * sys_auth_token 单行记录（token 存的是 SHA-256 哈希）
 */
@Data
public class SysAuthToken {

    private Long id;
    private Long userId;
    private String accessToken;
    private String refreshToken;
    private LocalDateTime accessExpiresAt;
    private LocalDateTime refreshExpiresAt;
    private LocalDateTime createdAt;
}

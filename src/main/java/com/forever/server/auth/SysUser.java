package com.forever.server.auth;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * sys_user 实体（字段映射依赖 mybatis 的 map-underscore-to-camel-case）。
 */
@Data
public class SysUser {

    private Long id;
    private String username;
    private String password;
    private String nickname;
    private LocalDateTime createdAt;
}

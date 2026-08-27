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
    /** ACTIVE / DISABLED；DISABLED 登录被拒且立即失去全部权限 */
    private String status;
    private LocalDateTime createdAt;
}

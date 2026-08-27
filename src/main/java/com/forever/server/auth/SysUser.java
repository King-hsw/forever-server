package com.forever.server.auth;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * sys_user 实体（字段映射依赖 mybatis 的 map-underscore-to-camel-case）。
 * 角色不在本表，经 sys_user_role 关联（见 RbacMapper）。
 */
@Data
public class SysUser {

    private Long id;
    private String username;
    private String password;
    private String nickname;
    /** ACTIVE / DISABLED；DISABLED 登录被拒且立即失去全部权限 */
    private String status;
    /** 非表字段：用户角色（管理端列表组装） */
    private List<SysRole> roles;
    private LocalDateTime createdAt;
}

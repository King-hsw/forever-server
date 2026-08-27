package com.forever.server.auth;

import lombok.Data;

import java.time.LocalDateTime;

/** 角色 */
@Data
public class SysRole {

    private Long id;
    private String code;
    private String name;
    private String remark;
    /** 内置角色不可删除，权限可调配 */
    private boolean builtIn;
    private LocalDateTime createdAt;
}

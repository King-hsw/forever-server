package com.forever.server.auth;

import lombok.Data;

import java.time.LocalDateTime;

/** 权限点。code 被接口鉴权引用（如 moment:post），name/module 仅供后台展示。 */
@Data
public class SysPermission {

    private Long id;
    private String code;
    private String name;
    /** 后台分组展示用：系统 / 朋友圈 / 书城… */
    private String module;
    private LocalDateTime createdAt;
}

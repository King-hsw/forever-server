package com.forever.server.auth;

import java.lang.annotation.*;

/**
 * 端点级权限声明（/api/admin/** 下的接口必须声明）：方法级或类级。
 * <ul>
 *   <li>{@code @Perm("article:create")} —— 校验唯一权限码，并携带权限点元数据；</li>
 *   <li>裸 {@code @Perm} —— 显式声明"仅需登录态"，不挂权限点。</li>
 * </ul>
 * 鉴权：{@link PermInterceptor}（WebConfig 注册于 /api/admin/**）按 {@link #value()} 校验当前用户；
 * 未声明 {@code @Perm} 的端点一律拒绝（fail-closed），保证每个端点的权限要求从注解即可读出。
 * 入库：启动时 {@link PermissionAutoRegistrar} 自动补 {@code sys_permission} 行并授予内置 ADMIN 角色，
 * 权限数据无需手工维护。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Perm {
    /**
     * 权限码（如 article:create），校验与授权共用；命名约定 module:action；留空 = 显式"仅需登录态"
     */
    String value() default "";

    /**
     * 显示名；留空自动取方法 @Operation summary，再空用权限码本身
     */
    String name() default "";

    /**
     * 模块分组；留空自动取类 @Tag name，再空用权限码冒号前段
     */
    String module() default "";
}

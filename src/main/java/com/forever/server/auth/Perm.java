package com.forever.server.auth;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 端点级权限声明：为一个接口（方法级）或整层（类级）声明唯一权限码，并携带权限点元数据。
 * <p>
 * 鉴权：{@link PermInterceptor}（WebConfig 注册于 /api/admin/**）按 {@link #value()} 校验当前用户；
 * 入库：启动时 {@link PermissionAutoRegistrar} 自动补 {@code sys_permission} 行并授予内置 ADMIN 角色。
 * 新增接口只需加这一个注解，权限数据无需手工维护。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Perm {
    /** 权限码（如 article:create），校验与授权共用；命名约定 module:action */
    String value();

    /** 显示名；留空自动取方法 @Operation summary，再空用权限码本身 */
    String name() default "";

    /** 模块分组；留空自动取类 @Tag name，再空用权限码冒号前段 */
    String module() default "";
}

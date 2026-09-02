package com.forever.server.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.lang.reflect.Method;

/**
 * {@link Perm} 鉴权拦截器（WebConfig 注册于 /api/admin/**，先于审计拦截器执行）。
 * <p>
 * 从方法/类的 {@link Perm} 解析权限码，与当前请求的 authority 集合比对
 * （authTokenFilter 按 RbacService 权限缓存构建）；未授权抛 AccessDeniedException，
 * GlobalExceptionHandler 统一 403。
 * <p>
 * 空 {@code @Perm} 显式表示仅需登录态直接放行；完全未声明 {@code @Perm} 属编码错误，直接拒绝。
 */
@Component
public class PermInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        Perm perm = resolve(handlerMethod.getMethod());
        if (perm == null) {
            throw new IllegalStateException("missing @Perm declaration: " + request.getRequestURI());
        }
        String code = perm.value();
        if (code.isEmpty()) {
            return true; // 显式声明：仅受 URL 级登录校验约束
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            for (GrantedAuthority authority : auth.getAuthorities()) {
                if (code.equals(authority.getAuthority())) {
                    return true;
                }
            }
        }
        throw new AccessDeniedException("no permission: " + code);
    }

    /**
     * 方法级 @Perm 优先，其次类级；均支持 CGLIB 代理类（按签名回溯用户类方法）。
     */
    private static Perm resolve(Method method) {
        Perm perm = AnnotatedElementUtils.findMergedAnnotation(method, Perm.class);
        if (perm != null) {
            return perm;
        }
        return AnnotatedElementUtils.findMergedAnnotation(
                ClassUtils.getUserClass(method.getDeclaringClass()), Perm.class);
    }
}

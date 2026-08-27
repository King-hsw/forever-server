package com.forever.server.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 权限拦截自检：未声明 @Perm 拒绝、裸 @Perm 放行、权限码匹配/拒绝 */
class PermInterceptorTest {

    static class Ctl {
        @Perm("x:read")
        void guarded() {}

        @Perm
        void open() {}

        void unmarked() {}
    }

    private final PermInterceptor interceptor = new PermInterceptor();

    private static HandlerMethod handler(String name) {
        try {
            return new HandlerMethod(new Ctl(), Ctl.class.getDeclaredMethod(name));
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
    }

    private static MockHttpServletRequest request() {
        var req = new MockHttpServletRequest();
        req.setRequestURI("/api/admin/x");
        return req;
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 未声明Perm的端点直接拒绝() {
        var e = assertThrows(IllegalStateException.class,
                () -> interceptor.preHandle(request(), null, handler("unmarked")));
        assertTrue(e.getMessage().contains("/api/admin/x"));
    }

    @Test
    void 裸Perm显式仅需登录态放行() {
        assertTrue(interceptor.preHandle(request(), null, handler("open")));
    }

    @Test
    void 权限码匹配放行不匹配拒绝() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("u", null,
                        List.of(new SimpleGrantedAuthority("x:read"))));
        assertTrue(interceptor.preHandle(request(), null, handler("guarded")));
        SecurityContextHolder.clearContext();
        assertThrows(AccessDeniedException.class,
                () -> interceptor.preHandle(request(), null, handler("guarded")));
    }
}

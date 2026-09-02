package com.forever.server.actionlog;

import com.forever.server.common.Web;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 后台写操作审计拦截器：自动记录 /api/admin/** 下的 POST/PUT/DELETE/PATCH，
 * 无需在各接口上打注解。GET 不记（读操作无破坏性，量也大）。
 * 记录失败只打运行日志，绝不影响业务请求。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogInterceptor implements HandlerInterceptor {

    private static final String ATTR_START = AuditLogInterceptor.class.getName() + ".start";

    private final ActionLogService actionLogService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(ATTR_START, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        try {
            if (!isAudited(request)) {
                return;
            }
            long start = (long) request.getAttribute(ATTR_START);
            actionLogService.record(
                    currentUsername(),
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    Web.clientIp(request),
                    System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("audit interceptor failed: path={}, reason={}", request.getRequestURI(), e.getMessage());
        }
    }

    private static boolean isAudited(HttpServletRequest request) {
        return switch (request.getMethod()) {
            case "POST", "PUT", "DELETE", "PATCH" -> true;
            default -> false;
        };
    }

    /**
     * JWT 认证通过后 SecurityContext 中有操作人；匿名请求返回 anonymous
     */
    private static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() ? auth.getName() : null;
    }
}

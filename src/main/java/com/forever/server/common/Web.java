package com.forever.server.common;

import jakarta.servlet.http.HttpServletRequest;

/** Servlet 请求工具 */
public final class Web {

    private Web() {
    }

    /** 优先取代理转发头，兼容 Nginx 反代部署 */
    public static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

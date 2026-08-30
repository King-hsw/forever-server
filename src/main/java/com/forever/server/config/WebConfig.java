package com.forever.server.config;

import com.forever.server.actionlog.AuditLogInterceptor;
import com.forever.server.auth.PermInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 文件读取为 RustFS 公开桶直链，不经应用；本类仅保留拦截器注册。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuditLogInterceptor auditLogInterceptor;
    private final PermInterceptor permInterceptor;

    public WebConfig(AuditLogInterceptor auditLogInterceptor,
                     PermInterceptor permInterceptor) {
        this.auditLogInterceptor = auditLogInterceptor;
        this.permInterceptor = permInterceptor;
    }

    /** 后台写操作自动审计；登录成败由 AuthService 显式记录，这里不重复 */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(permInterceptor).addPathPatterns("/api/admin/**");
        registry.addInterceptor(auditLogInterceptor).addPathPatterns("/api/admin/**");
    }
}

package com.forever.server.config;

import com.forever.server.actionlog.AuditLogInterceptor;
import com.forever.server.auth.PermInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * /uploads/** 的访问由 UploadsController 统一 302 到对象存储，静态资源映射不再涉及。
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

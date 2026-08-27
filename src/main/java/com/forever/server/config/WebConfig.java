package com.forever.server.config;

import com.forever.server.actionlog.AuditLogInterceptor;
import com.forever.server.auth.PermInterceptor;
import com.forever.server.auth.ProfileService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuditLogInterceptor auditLogInterceptor;
    private final PermInterceptor permInterceptor;

    public WebConfig(AuditLogInterceptor auditLogInterceptor, PermInterceptor permInterceptor) {
        this.auditLogInterceptor = auditLogInterceptor;
        this.permInterceptor = permInterceptor;
    }

    /** 后台写操作自动审计；登录成败由 AuthService 显式记录，这里不重复 */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(permInterceptor).addPathPatterns("/api/admin/**");
        registry.addInterceptor(auditLogInterceptor).addPathPatterns("/api/admin/**");
    }

    /** 用户上传文件（头像）：/uploads/** -> data/uploads/** */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(ProfileService.UPLOAD_ROOT.toAbsolutePath().toUri().toString());
    }
}

package com.forever.server.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 文档配置：
 * - Swagger UI:  /swagger-ui.html
 * - OpenAPI JSON: /v3/api-docs
 * 管理端接口通过右上角 Authorize 按钮填入登录返回的 accessToken（Bearer）调试。
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("forever-server API")
                        .description("个人博客后台 REST API。公开接口无需认证；/api/admin/** 需先调用 /api/auth/login 获取 accessToken。")
                        .version("0.1.0"))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME_NAME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new io.swagger.v3.oas.models.security.SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }
}

package com.forever.server.config;

import com.forever.server.auth.RbacService;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private static final String UNAUTHORIZED_BODY =
            "{\"code\":40101,\"message\":\"未登录或凭证无效\",\"data\":null}";
    private static final String FORBIDDEN_BODY =
            "{\"code\":40301,\"message\":\"无权限\",\"data\":null}";

    @Bean
    public SecretKey jwtSecretKey(BlogProperties props) {
        // HS256 要求密钥 ≥ 256 位（32 字节）
        return new SecretKeySpec(
                props.jwt().secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * JWT -> Authentication：从 RBAC 缓存取用户权限码作为 authorities。
     * 权限码即 authority 名（如 admin:access），可用 hasAuthority(...) 校验；
     * 后台调配角色权限后即时生效（缓存随变更失效）。
     */
    @Bean
    public org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
    jwtAuthenticationConverter(RbacService rbac) {
        var converter = new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            long uid = jwt.getClaim("uid");
            return rbac.permissionsOf(uid).stream()
                    .map(code -> (GrantedAuthority) () -> code)
                    .collect(java.util.stream.Collectors.toList());
        });
        return converter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 登录接口
                        .requestMatchers("/api/auth/login").permitAll()
                        // 前台公开接口（含访客评论的读写）与本站 RSS 输出
                        .requestMatchers("/api/v1/**", "/rss").permitAll()
                        // 上传文件静态访问、健康检查与 API 文档
                        .requestMatchers("/uploads/**", "/actuator/health",
                                "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // 当前登录人信息：任意登录用户可查
                        .requestMatchers("/api/admin/me").authenticated()
                        // 后台其余接口：需 admin:access 权限（RBAC 可配）
                        .requestMatchers("/api/admin/**").hasAuthority("admin:access")
                        // 其余一律需要认证
                        .anyRequest().authenticated())
                .oauth2ResourceServer(rs -> rs.jwt(jwt -> {
                }))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> {
                            log.warn("unauthenticated request rejected: {} {}", req.getMethod(), req.getRequestURI());
                            writeJson(res, HttpServletResponse.SC_UNAUTHORIZED, UNAUTHORIZED_BODY);
                        })
                        .accessDeniedHandler((req, res, e) -> {
                            log.warn("access denied: {} {}", req.getMethod(), req.getRequestURI());
                            writeJson(res, HttpServletResponse.SC_FORBIDDEN, FORBIDDEN_BODY);
                        }));
        return http.build();
    }

    private static void writeJson(HttpServletResponse response, int status, String body) {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        try {
            response.getWriter().write(body);
        } catch (Exception ignored) {
            // 响应已提交时忽略
        }
    }
}

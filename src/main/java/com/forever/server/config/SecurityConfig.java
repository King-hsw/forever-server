package com.forever.server.config;

import com.forever.server.auth.RbacService;
import com.forever.server.auth.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private static final String UNAUTHORIZED_BODY =
            "{\"code\":40101,\"message\":\"未登录或凭证无效\",\"data\":null}";
    private static final String FORBIDDEN_BODY =
            "{\"code\":40301,\"message\":\"无权限\",\"data\":null}";

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** 登录态过滤器：从 Bearer 头解析双 Token 的 access 侧，权限按 RBAC 权限码授予 */
    @Bean
    public OncePerRequestFilter authTokenFilter(TokenService tokenService, RbacService rbacService) {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                            FilterChain chain) throws ServletException, IOException {
                String header = request.getHeader("Authorization");
                if (header != null && header.startsWith("Bearer ")) {
                    try {
                        var principal = tokenService.resolve(header.substring(7).trim());
                        if (principal != null) {
                            List<GrantedAuthority> authorities = rbacService.permissionsOf(principal.uid()).stream()
                                    .map(code -> (GrantedAuthority) () -> code)
                                    .toList();
                            SecurityContextHolder.getContext().setAuthentication(
                                    new UsernamePasswordAuthenticationToken(principal, null, authorities));
                        }
                    } catch (org.springframework.security.core.AuthenticationException e) {
                        // 用户被删/禁用：按未认证处理，由授权规则决定放行或拒绝
                    }
                }
                chain.doFilter(request, response);
            }
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, OncePerRequestFilter authTokenFilter) throws Exception {
        http
                .cors(cors -> {
                })
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(
                        org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 登录/换发/登出接口
                        .requestMatchers("/api/auth/**").permitAll()
                        // 前台公开接口（含访客评论的读写）与本站 RSS 输出
                        .requestMatchers("/api/v1/**", "/rss").permitAll()
                        // 用户上传文件（头像等），公开可读
                        .requestMatchers("/uploads/**").permitAll()
                        // 健康检查与 API 文档
                        .requestMatchers("/actuator/health",
                                "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // 后台其余接口：登录即可进入
                        .requestMatchers("/api/admin/**").authenticated()
                        // 其余一律需要认证
                        .anyRequest().authenticated())
                // 双 Token 过滤器替代原 JWT resource server
                .addFilterBefore(authTokenFilter, UsernamePasswordAuthenticationFilter.class)
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

    /**
     * 认证走 Authorization 头（浏览器不会自动携带），非 Cookie，放开来源无风险；
     * 若改为 Cookie 会话需收敛到白名单。
     */
    @Bean
    public org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource() {
        var config = new org.springframework.web.cors.CorsConfiguration();
        config.setAllowedOriginPatterns(java.util.List.of("*"));
        config.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(java.util.List.of("*"));
        var source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
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

package com.forever.server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;
import java.time.Duration;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final BlogProperties props;

    public WebConfig(BlogProperties props) {
        this.props = props;
    }

    /** 将 /uploads/** 映射到本地磁盘上传目录，并带缓存头；生产可改由 nginx 直接托管 */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String baseDir = Paths.get(props.upload().baseDir())
                .toAbsolutePath().normalize().toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + baseDir + "/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(30)));
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(props.cors().allowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .maxAge(3600);
    }
}

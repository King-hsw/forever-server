package com.forever.server.moment;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 高德 Web Service key（动态页「获取当前位置」逆地理，见 {@link AmapService}）；
 * 留空 = 功能关闭，用户可手动填写地点
 */
@ConfigurationProperties(prefix = "blog.moments.amap")
public record AmapProperties(String key) {
}

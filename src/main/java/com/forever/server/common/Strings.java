package com.forever.server.common;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.springframework.util.DigestUtils;

/** 字符串与 URL 校验工具 */
public final class Strings {

    private Strings() {
    }

    /** 空白串归一为 null */
    public static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** 压平空白后截断（通知摘要用：看不完整内容，点击进页面） */
    public static String excerpt(String s, int max) {
        String flat = (s == null ? "" : s).replaceAll("\\s+", " ").trim();
        return flat.length() <= max ? flat : flat.substring(0, max) + "…";
    }

    /** 校验并返回合法的 http(s) 地址 */
    public static String checkHttpUrl(String url, String label) {
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme();
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                throw new IllegalArgumentException();
            }
        } catch (Exception e) {
            throw new BizException(ErrorCode.BAD_REQUEST, label + "必须是合法的 http(s) 地址");
        }
        return url.trim();
    }

    /** 邮箱 → Gravatar 头像（Cravatar 国内镜像）；邮箱为空（登录用户未填资料邮箱）返回 null */
    public static String gravatarUrl(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        String hash = DigestUtils.md5DigestAsHex(
                email.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
        return "https://cravatar.cn/avatar/" + hash + "?d=mp&s=80";
    }
}

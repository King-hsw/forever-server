package com.forever.server.common;

import java.net.URI;

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
}

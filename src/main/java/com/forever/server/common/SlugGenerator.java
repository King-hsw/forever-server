package com.forever.server.common;

import java.security.SecureRandom;

/**
 * slug 生成器：优先从标题提取语义化 slug（SEO 友好），无法提取时回退随机串。
 */
public final class SlugGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";

    /** 语义化 slug 最大长度，避免超长标题生成超长 URL */
    private static final int MAX_SEMANTIC_LENGTH = 80;

    private SlugGenerator() {
    }

    /**
     * 从标题生成语义化 slug：取连续的英文/数字词段，小写后用连字符拼接。
     * 例："Docker 部署 Spring Boot + Nuxt 全栈应用" -> "docker-spring-boot-nuxt"。
     * 中文等非 ASCII 字符不保留（URL 中需转码且不可读）；提取结果为空时回退随机串。
     */
    public static String fromTitle(String title) {
        if (title == null || title.isBlank()) {
            return randomSlug();
        }
        String slug = title.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (slug.isEmpty()) {
            return randomSlug();
        }
        if (slug.length() > MAX_SEMANTIC_LENGTH) {
            // 截断到词边界，避免尾部残留半个词或连字符
            slug = slug.substring(0, MAX_SEMANTIC_LENGTH);
            int lastHyphen = slug.lastIndexOf('-');
            if (lastHyphen > 0) {
                slug = slug.substring(0, lastHyphen);
            }
        }
        return slug;
    }

    public static String randomSlug() {
        StringBuilder sb = new StringBuilder(Long.toString(System.currentTimeMillis(), 36));
        for (int i = 0; i < 6; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}

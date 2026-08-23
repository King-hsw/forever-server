package com.forever.server.common;

import java.security.SecureRandom;

/**
 * slug 生成器：时间戳(base36) + 6 位随机串，冲突由调用方重试。
 */
public final class SlugGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";

    private SlugGenerator() {
    }

    public static String randomSlug() {
        StringBuilder sb = new StringBuilder(Long.toString(System.currentTimeMillis(), 36));
        for (int i = 0; i < 6; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}

package com.forever.server.sensitive;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "敏感词条目")
public record SensitiveWordResponse(
        Long id,
        String word,
        String replacement,
        LocalDateTime createdAt) {
}

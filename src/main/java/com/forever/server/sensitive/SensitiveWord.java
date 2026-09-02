package com.forever.server.sensitive;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 敏感词：命中后按 replacement 打码
 */
@Data
public class SensitiveWord {

    private Long id;
    private String word;
    private String replacement;
    private LocalDateTime createdAt;
}

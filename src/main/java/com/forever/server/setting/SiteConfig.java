package com.forever.server.setting;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * sys_site_config 单行记录
 */
@Data
public class SiteConfig {

    private String configKey;

    private String configValue;

    private LocalDateTime updatedAt;
}

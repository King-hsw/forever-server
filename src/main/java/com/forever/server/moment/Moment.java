package com.forever.server.moment;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 动态（朋友圈）。media 以 JSON 文本存储：{"images":[...],"audio":...,"video":...}
 */
@Data
public class Moment {

    private Long id;
    /** sys_user.id */
    private Long uid;
    private String content;
    /** JSON：{"images":["url",...],"audio":url|null,"video":url|null} */
    private String media;
    private String location;
    private BigDecimal lat;
    private BigDecimal lng;
    private LocalDateTime createdAt;
}

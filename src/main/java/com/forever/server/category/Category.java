package com.forever.server.category;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Category {

    private Long id;
    private String name;
    private String slug;
    private Integer sort;
    private LocalDateTime createdAt;
}

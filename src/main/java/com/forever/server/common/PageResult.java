package com.forever.server.common;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 统一分页结构：data = {list, total, page, size}
 */
@Schema(description = "统一分页结构")
public record PageResult<T>(
        @Schema(description = "当前页数据") List<T> list,
        @Schema(description = "总条数", example = "42") long total,
        @Schema(description = "当前页码，从 1 开始", example = "1") int page,
        @Schema(description = "每页条数", example = "10") int size) {

    public static <T> PageResult<T> of(List<T> list, long total, int page, int size) {
        return new PageResult<>(list, total, page, size);
    }
}

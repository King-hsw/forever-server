package com.forever.server.article;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 文章创建/更新请求（更新时全量提交） */
@Schema(description = "文章创建/更新请求（更新时全量提交，未传的字段会被置空）")
public record ArticleSaveRequest(
        @Schema(description = "标题", example = "Spring Boot 4 新特性概览")
        @NotBlank(message = "标题不能为空") @Size(max = 200) String title,
        @Schema(description = "正文（Markdown）")
        @NotBlank(message = "正文不能为空") String content,
        @Schema(description = "摘要；不填则发布阶段自动截取正文前 120 字")
        @Size(max = 500) String summary,
        @Schema(description = "URL 别名，供前台 /articles/{slug} 使用；不填服务端自动生成", example = "spring-boot-4")
        @Size(max = 200) String slug,
        @Schema(description = "封面图 URL")
        @Size(max = 500) String coverImage,
        @Schema(description = "分类 id；可不填", example = "1")
        Long categoryId,
        @Schema(description = "标签 id 列表；全量覆盖文章已有标签", example = "[1, 3]")
        List<Long> tagIds) {
}

package com.forever.server.article;

import com.forever.server.common.ApiResponse;
import com.forever.server.common.PageParams;
import com.forever.server.common.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "文章·公开接口", description = "前台展示接口，无需认证；仅返回已发布文章，URL 使用 slug")
@RestController
@RequestMapping("/api/v1/articles")
@RequiredArgsConstructor
public class PublicArticleController {

    private final ArticleService articleService;

    @Operation(summary = "分页查询已发布文章", description = "仅返回 PUBLISHED 状态的文章")
    @GetMapping
    public ApiResponse<PageResult<ArticleResponse>> page(
            @Parameter(description = "页码，从 1 开始", example = "1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数（最大 100）", example = "10") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "关键词，模糊匹配标题") @RequestParam(required = false) String keyword,
            @Parameter(description = "分类 id 过滤") @RequestParam(required = false) Long categoryId,
            @Parameter(description = "标签 id 过滤") @RequestParam(required = false) Long tagId) {
        return ApiResponse.ok(articleService.pagePublic(
                page, PageParams.normalizeSize(size), keyword, categoryId, tagId));
    }

    @Operation(summary = "文章归档", description = "仅返回 PUBLISHED 状态的文章，按发布时间倒序")
    @GetMapping("/archive")
    public ApiResponse<List<ArticleArchiveItem>> archive() {
        return ApiResponse.ok(articleService.listArchive());
    }

    @Operation(summary = "文章详情", description = "按 slug 查询；每次访问浏览量 +1")
    @GetMapping("/{slug}")
    public ApiResponse<ArticleResponse> detail(@Parameter(description = "文章 URL 别名", example = "spring-boot-4") @PathVariable String slug) {
        return ApiResponse.ok(articleService.detailPublic(slug));
    }
}

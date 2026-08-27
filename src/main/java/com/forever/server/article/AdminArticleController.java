package com.forever.server.article;

import com.forever.server.common.ApiResponse;
import com.forever.server.common.PageParams;
import com.forever.server.common.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "文章管理", description = "管理端文章接口，需 JWT 认证；URL 使用数字 id")
@PreAuthorize("hasAuthority('article:manage')")
@RestController
@RequestMapping("/api/admin/articles")
public class AdminArticleController {

    private final ArticleService articleService;
    private final AiSummaryService aiSummaryService;

    public AdminArticleController(ArticleService articleService, AiSummaryService aiSummaryService) {
        this.articleService = articleService;
        this.aiSummaryService = aiSummaryService;
    }

    @Operation(summary = "分页查询文章", description = "管理端全量查询，含草稿；keyword 模糊匹配标题")
    @GetMapping
    public ApiResponse<PageResult<ArticleResponse>> page(
            @Parameter(description = "页码，从 1 开始", example = "1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数（最大 100）", example = "10") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "状态过滤：DRAFT / PUBLISHED，不传查全部", example = "PUBLISHED") @RequestParam(required = false) String status,
            @Parameter(description = "关键词，模糊匹配标题") @RequestParam(required = false) String keyword,
            @Parameter(description = "分类 id 过滤") @RequestParam(required = false) Long categoryId) {
        return ApiResponse.ok(articleService.pageAdmin(
                page, PageParams.normalizeSize(size),
                parseStatus(status), keyword, categoryId));
    }

    @Operation(summary = "创建文章", description = "创建后默认为 DRAFT 状态，需调用发布接口上线")
    @PostMapping
    public ApiResponse<ArticleResponse> create(@Valid @RequestBody ArticleSaveRequest request) {
        return ApiResponse.ok(articleService.create(request));
    }

    @Operation(summary = "文章详情")
    @GetMapping("/{id}")
    public ApiResponse<ArticleResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok(articleService.getById(id));
    }

    @Operation(summary = "更新文章", description = "全量更新：未传的字段会被置空；tagIds 全量覆盖原有标签")
    @PutMapping("/{id}")
    public ApiResponse<ArticleResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody ArticleSaveRequest request) {
        return ApiResponse.ok(articleService.update(id, request));
    }

    @Operation(summary = "删除文章")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        articleService.delete(id);
        return ApiResponse.ok();
    }

    @Operation(summary = "发布文章", description = "DRAFT → PUBLISHED；摘要为空时自动截取正文前 120 字")
    @PutMapping("/{id}/publish")
    public ApiResponse<Void> publish(@PathVariable Long id) {
        articleService.publish(id);
        return ApiResponse.ok();
    }

    @Operation(summary = "下线文章", description = "PUBLISHED → DRAFT，前台立即不可见")
    @PutMapping("/{id}/unpublish")
    public ApiResponse<Void> unpublish(@PathVariable Long id) {
        articleService.unpublish(id);
        return ApiResponse.ok();
    }

    @Operation(summary = "AI 生成概要", description = "调大模型为文章正文生成中文摘要并写入 summary；需在站点设置中开启 ai.summary-enabled 并配置 ai.api-key")
    @PostMapping("/{id}/ai-summary")
    public ApiResponse<ArticleResponse> aiSummary(@PathVariable Long id) {
        String summary = aiSummaryService.generate(id);
        return ApiResponse.ok(articleService.getById(id));
    }

    private static ArticleStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return ArticleStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

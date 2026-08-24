package com.forever.server.search;

import com.forever.server.common.ApiResponse;
import com.forever.server.common.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "全局搜索·公开接口", description = "按标题/摘要/正文模糊搜索已发布文章，无需登录")
@RestController
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @Operation(summary = "全局搜索", description = """
            匹配标题、摘要或正文；标题命中优先，摘要次之，正文兜底，同分按发布时间倒序。
            keyword 为空返回空结果；超长截断到 100 字符。
            highlights 中文本已做 HTML 转义，仅 <em> 为高亮标签。""")
    @GetMapping("/api/v1/search")
    public ApiResponse<PageResult<SearchItemResponse>> search(
            @Parameter(description = "关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "页码，从 1 开始", example = "1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数（最大 100）", example = "10") @RequestParam(defaultValue = "10") int size) {
        size = Math.min(Math.max(size, 1), 100);
        page = Math.max(page, 1);
        return ApiResponse.ok(searchService.search(keyword, page, size));
    }
}

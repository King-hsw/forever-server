package com.forever.server.rss;

import com.forever.server.common.ApiResponse;
import com.forever.server.common.PageParams;
import com.forever.server.common.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "RSS·公开接口", description = "前台展示接口：订阅的文章流与订阅源列表，无需认证")
@RestController
@RequestMapping("/api/v1/rss")
public class PublicRssController {

    private final RssService rssService;

    public PublicRssController(RssService rssService) {
        this.rssService = rssService;
    }

    @Operation(summary = "分页查询最新订阅文章", description = "按发布时间倒序，仅包含启用中的订阅源")
    @GetMapping("/items")
    public ApiResponse<PageResult<RssItemResponse>> items(
            @Parameter(description = "页码，从 1 开始", example = "1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数（最大 100）", example = "20") @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(rssService.pageItems(page, PageParams.normalizeSize(size)));
    }

    @Operation(summary = "订阅源列表", description = "供前台展示博客朋友圈，仅返回启用中的源")
    @GetMapping("/feeds")
    public ApiResponse<List<RssFeedResponse>> feeds() {
        return ApiResponse.ok(rssService.listEnabledFeeds());
    }
}

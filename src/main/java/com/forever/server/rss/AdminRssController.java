package com.forever.server.rss;

import com.forever.server.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "RSS 订阅管理", description = "管理端订阅源维护接口，需 JWT 认证")
@RestController
@RequestMapping("/api/admin/rss/feeds")
public class AdminRssController {

    private final RssService rssService;

    public AdminRssController(RssService rssService) {
        this.rssService = rssService;
    }

    @Operation(summary = "订阅源列表", description = "全量返回，附带每个源的条目数、上次抓取时间与错误信息")
    @GetMapping
    public ApiResponse<List<RssFeedResponse>> list() {
        return ApiResponse.ok(rssService.listFeeds());
    }

    @Operation(summary = "添加订阅源", description = "feedUrl 不可重复；创建成功后立即首抓一次（失败不影响创建）")
    @PostMapping
    public ApiResponse<RssFeedResponse> create(@Valid @RequestBody RssFeedRequest request) {
        return ApiResponse.ok(rssService.create(request));
    }

    @Operation(summary = "更新订阅源", description = "全量更新：未传的字段会被置空")
    @PutMapping("/{id}")
    public ApiResponse<RssFeedResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody RssFeedRequest request) {
        return ApiResponse.ok(rssService.update(id, request));
    }

    @Operation(summary = "删除订阅源", description = "同时删除该源已抓取的全部条目")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        rssService.delete(id);
        return ApiResponse.ok();
    }

    @Operation(summary = "手动刷新", description = "立即抓取该源一次；失败会记录到该源的 lastError 字段")
    @PostMapping("/{id}/refresh")
    public ApiResponse<Void> refresh(@PathVariable Long id) {
        rssService.refresh(id);
        return ApiResponse.ok();
    }
}

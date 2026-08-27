package com.forever.server.actionlog;

import com.forever.server.common.ApiResponse;
import com.forever.server.common.PageResult;
import com.forever.server.common.Strings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "审计日志", description = "后台写操作与登录记录，供安全追溯与排查")
@PreAuthorize("hasAuthority('log:read')")
@RestController
@RequestMapping("/api/admin/logs")
public class AdminActionLogController {

    private final ActionLogService actionLogService;

    public AdminActionLogController(ActionLogService actionLogService) {
        this.actionLogService = actionLogService;
    }

    @Operation(summary = "分页查询审计日志", description = "按时间倒序；支持按操作人精确过滤、按路径模糊过滤（如传 articles 可查所有文章相关操作）")
    @GetMapping
    public ApiResponse<PageResult<ActionLogResponse>> page(
            @Parameter(description = "页码，从 1 开始", example = "1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数（最大 100）", example = "20") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "操作人，精确匹配", example = "admin") @RequestParam(required = false) String username,
            @Parameter(description = "路径关键词，模糊匹配", example = "/api/admin/articles") @RequestParam(required = false) String path) {
        PageResult<ActionLog> result = actionLogService.page(page, size, Strings.blankToNull(username), Strings.blankToNull(path));
        List<ActionLogResponse> items = result.list().stream().map(ActionLogResponse::from).toList();
        return ApiResponse.ok(PageResult.of(items, result.total(), result.page(), result.size()));
    }
}

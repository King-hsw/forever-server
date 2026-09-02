package com.forever.server.sensitive;

import com.forever.server.auth.Perm;
import com.forever.server.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "敏感词管理", description = "评论敏感词库维护，需 JWT 认证；修改即时生效（内存缓存自动刷新）")
@RestController
@RequestMapping("/api/admin/sensitive-words")
@RequiredArgsConstructor
public class AdminSensitiveWordController {

    private final SensitiveWordService service;

    @Perm("sensitive:list")
    @Operation(summary = "词库列表", description = "全量返回，按 id 倒序")
    @GetMapping
    public ApiResponse<List<SensitiveWordResponse>> list() {
        return ApiResponse.ok(service.listAll());
    }

    @Perm("sensitive:create")
    @Operation(summary = "新增敏感词", description = "word 不可重复；replacement 不填默认 ***")
    @PostMapping
    public ApiResponse<SensitiveWordResponse> create(@Valid @RequestBody SensitiveWordRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @Perm("sensitive:update")
    @Operation(summary = "更新敏感词", description = "全量更新")
    @PutMapping("/{id}")
    public ApiResponse<SensitiveWordResponse> update(@PathVariable Long id,
                                                     @Valid @RequestBody SensitiveWordRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @Perm("sensitive:delete")
    @Operation(summary = "删除敏感词")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}

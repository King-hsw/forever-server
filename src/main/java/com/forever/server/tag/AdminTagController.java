package com.forever.server.tag;

import com.forever.server.auth.Perm;
import com.forever.server.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "标签管理", description = "管理端标签接口，需 JWT 认证")
@RestController
@RequestMapping("/api/admin/tags")
@RequiredArgsConstructor
public class AdminTagController {

    private final TagService tagService;

    @Perm("tag:list")
    @Operation(summary = "标签列表", description = "全量返回，附带每标签关联的已发布文章数")
    @GetMapping
    public ApiResponse<List<TagResponse>> list() {
        return ApiResponse.ok(tagService.listAll());
    }

    @Perm("tag:create")
    @Operation(summary = "创建标签", description = "名称不可重复")
    @PostMapping
    public ApiResponse<TagResponse> create(@Valid @RequestBody TagRequest request) {
        return ApiResponse.ok(tagService.create(request));
    }

    @Perm("tag:update")
    @Operation(summary = "更新标签", description = "重命名后所有关联文章同步生效")
    @PutMapping("/{id}")
    public ApiResponse<TagResponse> update(@PathVariable Long id,
                                           @Valid @RequestBody TagRequest request) {
        return ApiResponse.ok(tagService.update(id, request));
    }

    @Perm("tag:delete")
    @Operation(summary = "删除标签", description = "先清除所有文章与该标签的关联，再删除标签本身")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        tagService.delete(id);
        return ApiResponse.ok();
    }
}

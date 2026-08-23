package com.forever.server.tag;

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

@Tag(name = "标签管理", description = "管理端标签接口，需 JWT 认证")
@RestController
@RequestMapping("/api/admin/tags")
public class AdminTagController {

    private final TagService tagService;

    public AdminTagController(TagService tagService) {
        this.tagService = tagService;
    }

    @Operation(summary = "标签列表", description = "全量返回，附带每标签关联的已发布文章数")
    @GetMapping
    public ApiResponse<List<TagResponse>> list() {
        return ApiResponse.ok(tagService.listAll());
    }

    @Operation(summary = "创建标签", description = "名称不可重复")
    @PostMapping
    public ApiResponse<TagResponse> create(@Valid @RequestBody TagRequest request) {
        return ApiResponse.ok(tagService.create(request));
    }

    @Operation(summary = "更新标签", description = "重命名后所有关联文章同步生效")
    @PutMapping("/{id}")
    public ApiResponse<TagResponse> update(@PathVariable Long id,
                                           @Valid @RequestBody TagRequest request) {
        return ApiResponse.ok(tagService.update(id, request));
    }

    @Operation(summary = "删除标签", description = "先清除所有文章与该标签的关联，再删除标签本身")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        tagService.delete(id);
        return ApiResponse.ok();
    }
}

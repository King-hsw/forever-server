package com.forever.server.tag;

import com.forever.server.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 标签公开接口：列表附带已发布文章数 */
@RestController
@RequestMapping("/api/v1/tags")
public class PublicTagController {

    private final TagService tagService;

    public PublicTagController(TagService tagService) {
        this.tagService = tagService;
    }

    @GetMapping
    public ApiResponse<List<TagResponse>> list() {
        return ApiResponse.ok(tagService.listAll());
    }
}

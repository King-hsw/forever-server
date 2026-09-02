package com.forever.server.category;

import com.forever.server.common.BizException;
import com.forever.server.common.ErrorCode;
import com.forever.server.common.SlugGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 分类管理：名称唯一，slug 缺省随机生成；分类下仍有文章时禁止删除（防悬挂引用），
 * 列表携带各分类已发布文章数供前台展示。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryMapper categoryMapper;

    public List<CategoryResponse> listAll() {
        return categoryMapper.listWithPublishedCount().stream()
                .map(r -> new CategoryResponse(r.id(), r.name(), r.slug(),
                        r.sort() == null ? 0 : r.sort(),
                        r.articleCount() == null ? 0 : r.articleCount()))
                .toList();
    }

    public CategoryResponse create(CategoryRequest request) {
        checkNameUnique(request.name(), null);
        String slug = resolveSlug(request.slug());
        Category category = new Category();
        category.setName(request.name());
        category.setSlug(slug);
        category.setSort(request.sortOrDefault());
        categoryMapper.insert(category);
        log.info("category created: id={}, name={}", category.getId(), category.getName());
        return toResponse(category, 0);
    }

    public CategoryResponse update(Long id, CategoryRequest request) {
        Category exists = requireExists(id);
        checkNameUnique(request.name(), id);
        String slug = resolveSlugForUpdate(id, exists, request.slug());
        exists.setName(request.name());
        exists.setSlug(slug);
        exists.setSort(request.sortOrDefault());
        categoryMapper.update(exists);
        log.info("category updated: id={}, name={}", id, exists.getName());
        return toResponse(exists, 0);
    }

    public void delete(Long id) {
        requireExists(id);
        if (categoryMapper.countArticlesUsing(id) > 0) {
            log.warn("category delete rejected: id={}, articles still using it", id);
            throw new BizException(ErrorCode.CONFLICT, "该分类下仍有文章，无法删除");
        }
        categoryMapper.deleteById(id);
        log.info("category deleted: id={}", id);
    }

    // ---------- internal ----------

    Category requireExists(Long id) {
        Category category = categoryMapper.findById(id);
        if (category == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "分类不存在");
        }
        return category;
    }

    private void checkNameUnique(String name, Long excludeId) {
        long count = categoryMapper.countByName(name);
        if (excludeId == null ? count > 0 : count > 1) {
            throw new BizException(ErrorCode.CONFLICT, "分类名称已存在");
        }
    }

    private String resolveSlug(String requested) {
        String slug = (requested == null || requested.isBlank())
                ? SlugGenerator.randomSlug() : requested;
        if (categoryMapper.countBySlug(slug) > 0) {
            throw new BizException(ErrorCode.CONFLICT, "分类 slug 已存在");
        }
        return slug;
    }

    private String resolveSlugForUpdate(Long id, Category exists, String requested) {
        if (requested == null || requested.isBlank()) {
            return exists.getSlug();
        }
        if (requested.equals(exists.getSlug())) {
            return requested;
        }
        if (categoryMapper.countBySlug(requested) > 0) {
            throw new BizException(ErrorCode.CONFLICT, "分类 slug 已存在");
        }
        return requested;
    }

    private CategoryResponse toResponse(Category c, long articleCount) {
        return new CategoryResponse(c.getId(), c.getName(), c.getSlug(),
                c.getSort() == null ? 0 : c.getSort(), articleCount);
    }
}

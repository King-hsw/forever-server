package com.forever.server.category;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CategoryMapper {

    int insert(Category category);

    int update(Category category);

    int deleteById(Long id);

    Category findById(Long id);

    long countByName(String name);

    long countBySlug(String slug);

    /** 被文章引用的数量（含草稿与软删，只要引用就拒绝删除） */
    long countArticlesUsing(Long categoryId);

    /** 全部分类 + 已发布文章数（管理端与公开接口共用） */
    List<CategoryCountRow> listWithPublishedCount();

    record CategoryCountRow(Long id, String name, String slug, Integer sort, Long articleCount) {
    }
}

package com.forever.server.category;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface CategoryMapper {

    String WITH_PUBLISHED_COUNT = """
            SELECT c.id, c.name, c.slug, c.sort,
                   COUNT(a.id) AS article_count
            FROM category c
            LEFT JOIN article a
                   ON a.category_id = c.id AND a.status = 'PUBLISHED' AND a.deleted = false
            GROUP BY c.id
            """;

    @Insert("INSERT INTO category (name, slug, sort) VALUES (#{name}, #{slug}, #{sort})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Category category);

    @Update("""
            UPDATE category SET name = #{name}, slug = #{slug}, sort = #{sort} WHERE id = #{id}
            """)
    int update(Category category);

    @Delete("DELETE FROM category WHERE id = #{id}")
    int deleteById(Long id);

    @Select("SELECT * FROM category WHERE id = #{id}")
    Category findById(Long id);

    @Select("SELECT COUNT(*) FROM category WHERE name = #{name}")
    long countByName(String name);

    @Select("SELECT COUNT(*) FROM category WHERE slug = #{slug}")
    long countBySlug(String slug);

    /** 被文章引用的数量（含草稿与软删，只要引用就拒绝删除） */
    @Select("SELECT COUNT(*) FROM article WHERE category_id = #{categoryId} AND deleted = false")
    long countArticlesUsing(Long categoryId);

    /** 全部分类 + 已发布文章数（管理端与公开接口共用） */
    @Select(WITH_PUBLISHED_COUNT + " ORDER BY c.sort, c.id")
    List<CategoryCountRow> listWithPublishedCount();

    record CategoryCountRow(Long id, String name, String slug, Integer sort, Long articleCount) {
    }
}

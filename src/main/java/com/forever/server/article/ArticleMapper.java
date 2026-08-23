package com.forever.server.article;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ArticleMapper {

    String BASE_COLUMNS = """
            a.id, a.title, a.slug, a.summary, a.content, a.cover_image,
            a.category_id, c.name AS category_name, a.status, a.type, a.content_format, a.view_count,
            a.published_at, a.created_at, a.updated_at, a.deleted
            """;

    @Insert("""
            INSERT INTO article (title, slug, summary, content, cover_image, category_id, status, type, content_format)
            VALUES (#{title}, #{slug}, #{summary}, #{content}, #{coverImage}, #{categoryId}, #{status}, #{type}, #{contentFormat})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Article article);

    @Update("""
            UPDATE article
            SET title = #{title}, slug = #{slug}, summary = #{summary}, content = #{content},
                cover_image = #{coverImage}, category_id = #{categoryId},
                type = #{type}, content_format = #{contentFormat}, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND deleted = false
            """)
    int update(Article article);

    @Select("SELECT " + BASE_COLUMNS + " FROM article a LEFT JOIN category c ON c.id = a.category_id "
            + "WHERE a.id = #{id} AND a.deleted = false")
    Article findById(Long id);

    @Select("SELECT " + BASE_COLUMNS + " FROM article a LEFT JOIN category c ON c.id = a.category_id "
            + "WHERE a.slug = #{slug} AND a.deleted = false")
    Article findBySlug(String slug);

    @Select("SELECT COUNT(*) FROM article WHERE slug = #{slug} AND deleted = false")
    long existsBySlug(String slug);

    // ---------- 管理端分页（动态条件在 XML） ----------
    List<Article> adminPage(@Param("status") ArticleStatus status,
                            @Param("keyword") String keyword,
                            @Param("categoryId") Long categoryId,
                            @Param("offset") int offset,
                            @Param("size") int size);

    long countAdmin(@Param("status") ArticleStatus status,
                    @Param("keyword") String keyword,
                    @Param("categoryId") Long categoryId);

    // ---------- 公开分页：仅已发布 ----------
    List<Article> publicPage(@Param("keyword") String keyword,
                             @Param("categoryId") Long categoryId,
                             @Param("tagId") Long tagId,
                             @Param("offset") int offset,
                             @Param("size") int size);

    long countPublic(@Param("keyword") String keyword,
                     @Param("categoryId") Long categoryId,
                     @Param("tagId") Long tagId);

    // ---------- 公开归档：仅已发布，不取重字段 ----------
    @Select("SELECT id, title, slug, published_at FROM article "
            + "WHERE status = 'PUBLISHED' AND deleted = false ORDER BY published_at DESC")
    List<Article> selectArchive();

    // ---------- 状态与统计 ----------
    @Update("""
            UPDATE article
            SET status = 'PUBLISHED',
                published_at = COALESCE(published_at, CURRENT_TIMESTAMP),
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND deleted = false
            """)
    int publish(Long id);

    @Update("UPDATE article SET status = 'DRAFT', updated_at = CURRENT_TIMESTAMP WHERE id = #{id} AND deleted = false")
    int unpublish(Long id);

    @Update("UPDATE article SET view_count = view_count + 1 WHERE id = #{id}")
    int incrementViewCount(Long id);

    @Update("UPDATE article SET deleted = true, updated_at = CURRENT_TIMESTAMP WHERE id = #{id}")
    int softDelete(Long id);

    /** 批量取多篇文章的标签（联表），service 组装回各文章 */
    List<Article.TagItemRow> tagRowsForArticles(@Param("articleIds") List<Long> articleIds);
}

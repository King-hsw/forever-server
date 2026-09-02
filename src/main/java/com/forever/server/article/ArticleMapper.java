package com.forever.server.article;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ArticleMapper {

    int insert(Article article);

    int update(Article article);

    Article findById(Long id);

    Article findBySlug(String slug);

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
    List<Article> selectArchive();

    // ---------- 状态与统计 ----------
    int publish(Long id);

    int unpublish(Long id);

    int incrementViewCount(Long id);

    int softDelete(Long id);

    /**
     * 仅更新概要（AI 生成）
     */
    void updateSummary(@Param("id") Long id, @Param("summary") String summary);

    /**
     * 批量取多篇文章的标签（联表），service 组装回各文章
     */
    List<Article.TagItemRow> tagRowsForArticles(@Param("articleIds") List<Long> articleIds);
}

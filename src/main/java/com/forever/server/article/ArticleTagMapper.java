package com.forever.server.article;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 文章-标签关联 */
@Mapper
public interface ArticleTagMapper {

    @Insert("INSERT INTO article_tag (article_id, tag_id) VALUES (#{articleId}, #{tagId})")
    int insert(@Param("articleId") Long articleId, @Param("tagId") Long tagId);

    @Delete("DELETE FROM article_tag WHERE article_id = #{articleId}")
    int deleteByArticleId(Long articleId);

    @Select("SELECT tag_id FROM article_tag WHERE article_id = #{articleId}")
    List<Long> tagIdsByArticleId(Long articleId);
}

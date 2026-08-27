package com.forever.server.article;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 文章-标签关联 */
@Mapper
public interface ArticleTagMapper {

    int insert(@Param("articleId") Long articleId, @Param("tagId") Long tagId);

    int deleteByArticleId(Long articleId);
}

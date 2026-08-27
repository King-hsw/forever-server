package com.forever.server.search;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SearchMapper {

    /**
     * 全文命中行。相关度：标题命中权重最高，摘要次之，正文兜底；
     * word_similarity 由 pg_trgm 提供（V14 迁移启用）。
     */
    List<SearchRow> page(@Param("kw") String kw, @Param("offset") int offset, @Param("size") int size);

    long count(@Param("kw") String kw);

    /** 搜索结果行（content 仅用于生成摘要片段，不返回给前端；标签由 ArticleMapper.tagRowsForArticles 批量补齐） */
    record SearchRow(Long id, String slug, String title, String summary, String content,
                     String categoryName, LocalDateTime createdAt) {
    }
}

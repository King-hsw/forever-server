package com.forever.server.rss;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface RssItemMapper {

    /**
     * link 相同的条目只保留第一条，返回插入行数（0 表示已存在）
     */
    int insertIgnore(RssItem item);

    /**
     * 联表投影行：含来源站点信息，供公开列表直接展示。
     */
    record ItemRow(Long id, Long feedId, String feedTitle, String siteUrl,
                   String title, String link, String summary,
                   LocalDateTime publishedAt, LocalDateTime createdAt) {
    }

    List<ItemRow> pageLatest(@Param("offset") int offset, @Param("size") int size);

    long countLatest();

    long countByFeedId(Long feedId);

    void deleteByFeedId(Long feedId);
}

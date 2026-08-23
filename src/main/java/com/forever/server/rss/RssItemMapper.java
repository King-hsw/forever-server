package com.forever.server.rss;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface RssItemMapper {

    /** link 相同的条目只保留第一条，返回插入行数（0 表示已存在） */
    @Insert("""
            INSERT INTO rss_item (feed_id, link, title, summary, published_at)
            VALUES (#{feedId}, #{link}, #{title}, #{summary}, #{publishedAt})
            ON CONFLICT (feed_id, link) DO NOTHING
            """)
    int insertIgnore(RssItem item);

    String LATEST_PAGE = """
            SELECT i.id, i.feed_id, f.title AS feed_title, f.site_url AS site_url,
                   i.title, i.link, i.summary, i.published_at, i.created_at
            FROM rss_item i
            JOIN rss_feed f ON f.id = i.feed_id AND f.enabled = TRUE
            ORDER BY i.published_at DESC NULLS LAST, i.id DESC
            LIMIT #{size} OFFSET #{offset}
            """;

    /**
     * 联表投影行：含来源站点信息，供公开列表直接展示。
     */
    record ItemRow(Long id, Long feedId, String feedTitle, String siteUrl,
                   String title, String link, String summary,
                   LocalDateTime publishedAt, LocalDateTime createdAt) {
    }

    @Select(LATEST_PAGE)
    List<ItemRow> pageLatest(@Param("offset") int offset, @Param("size") int size);

    @Select("""
            SELECT COUNT(*)
            FROM rss_item i
            JOIN rss_feed f ON f.id = i.feed_id AND f.enabled = TRUE
            """)
    long countLatest();

    @Select("SELECT COUNT(*) FROM rss_item WHERE feed_id = #{feedId}")
    long countByFeedId(Long feedId);

    @Delete("DELETE FROM rss_item WHERE feed_id = #{feedId}")
    void deleteByFeedId(Long feedId);
}

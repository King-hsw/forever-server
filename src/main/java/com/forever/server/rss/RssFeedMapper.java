package com.forever.server.rss;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface RssFeedMapper {

    @Insert("""
            INSERT INTO rss_feed (title, site_url, feed_url, description, enabled)
            VALUES (#{title}, #{siteUrl}, #{feedUrl}, #{description}, #{enabled})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(RssFeed feed);

    @Update("""
            UPDATE rss_feed
            SET title = #{title}, site_url = #{siteUrl}, feed_url = #{feedUrl},
                description = #{description}, enabled = #{enabled}
            WHERE id = #{id}
            """)
    int update(RssFeed feed);

    @Delete("DELETE FROM rss_feed WHERE id = #{id}")
    int deleteById(Long id);

    @Select("SELECT * FROM rss_feed ORDER BY created_at DESC")
    List<RssFeed> findAll();

    @Select("SELECT * FROM rss_feed WHERE id = #{id}")
    RssFeed findById(Long id);

    @Select("SELECT * FROM rss_feed WHERE enabled = TRUE")
    List<RssFeed> findEnabled();

    @Select("SELECT COUNT(*) FROM rss_feed WHERE feed_url = #{feedUrl}")
    long countByFeedUrl(String feedUrl);

    /** 抓取成功：回填标题/描述、刷新时间、清空错误 */
    @Update("""
            UPDATE rss_feed
            SET title       = COALESCE(NULLIF(title, ''), #{fallbackTitle}),
                description = COALESCE(description, #{description}),
                last_fetched_at = #{now},
                last_error  = NULL
            WHERE id = #{id}
            """)
    void markSuccess(@Param("id") Long id,
                     @Param("fallbackTitle") String fallbackTitle,
                     @Param("description") String description,
                     @Param("now") LocalDateTime now);

    @Update("UPDATE rss_feed SET last_fetched_at = #{now}, last_error = #{error} WHERE id = #{id}")
    void markError(@Param("id") Long id, @Param("error") String error, @Param("now") LocalDateTime now);
}

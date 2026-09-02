package com.forever.server.rss;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface RssFeedMapper {

    int insert(RssFeed feed);

    int update(RssFeed feed);

    int deleteById(Long id);

    List<RssFeed> findAll();

    RssFeed findById(Long id);

    List<RssFeed> findEnabled();

    long countByFeedUrl(String feedUrl);

    /**
     * 抓取成功：回填标题/描述、刷新时间、清空错误
     */
    void markSuccess(@Param("id") Long id,
                     @Param("fallbackTitle") String fallbackTitle,
                     @Param("description") String description,
                     @Param("now") LocalDateTime now);

    void markError(@Param("id") Long id, @Param("error") String error, @Param("now") LocalDateTime now);
}

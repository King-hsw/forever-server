package com.forever.server.tag;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TagMapper {

    int insert(Tag tag);

    int update(Tag tag);

    int deleteById(Long id);

    int deleteRelationsByTagId(Long tagId);

    Tag findById(Long id);

    long countByName(String name);

    /**
     * 全部标签 + 已发布文章数
     */
    List<TagCountRow> listWithPublishedCount();

    record TagCountRow(Long id, String name, Long articleCount) {
    }
}

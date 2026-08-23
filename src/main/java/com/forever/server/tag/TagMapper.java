package com.forever.server.tag;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface TagMapper {

    String WITH_PUBLISHED_COUNT = """
            SELECT t.id, t.name, COUNT(a.id) AS article_count
            FROM tag t
            LEFT JOIN article_tag at_ ON at_.tag_id = t.id
            LEFT JOIN article a ON a.id = at_.article_id AND a.status = 'PUBLISHED' AND a.deleted = false
            GROUP BY t.id
            """;

    @Insert("INSERT INTO tag (name) VALUES (#{name})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Tag tag);

    @Update("UPDATE tag SET name = #{name} WHERE id = #{id}")
    int update(Tag tag);

    @Delete("DELETE FROM tag WHERE id = #{id}")
    int deleteById(Long id);

    @Delete("DELETE FROM article_tag WHERE tag_id = #{tagId}")
    int deleteRelationsByTagId(Long tagId);

    @Select("SELECT * FROM tag WHERE id = #{id}")
    Tag findById(Long id);

    @Select("SELECT COUNT(*) FROM tag WHERE name = #{name}")
    long countByName(String name);

    /** 全部标签 + 已发布文章数 */
    @Select(WITH_PUBLISHED_COUNT + " ORDER BY t.id")
    List<TagCountRow> listWithPublishedCount();

    record TagCountRow(Long id, String name, Long articleCount) {
    }
}

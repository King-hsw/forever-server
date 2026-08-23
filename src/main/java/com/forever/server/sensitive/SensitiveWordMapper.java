package com.forever.server.sensitive;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface SensitiveWordMapper {

    @Insert("INSERT INTO sensitive_word (word, replacement) VALUES (#{word}, #{replacement})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SensitiveWord word);

    @Update("UPDATE sensitive_word SET word = #{word}, replacement = #{replacement} WHERE id = #{id}")
    int update(SensitiveWord word);

    @Delete("DELETE FROM sensitive_word WHERE id = #{id}")
    int deleteById(Long id);

    @Select("SELECT * FROM sensitive_word ORDER BY id DESC")
    List<SensitiveWord> findAll();
}

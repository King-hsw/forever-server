package com.forever.server.sensitive;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SensitiveWordMapper {

    int insert(SensitiveWord word);

    int update(SensitiveWord word);

    int deleteById(Long id);

    List<SensitiveWord> findAll();
}

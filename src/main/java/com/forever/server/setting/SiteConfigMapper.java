package com.forever.server.setting;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SiteConfigMapper {

    List<SiteConfig> findAll();

    int upsert(@Param("key") String key, @Param("value") String value);
}

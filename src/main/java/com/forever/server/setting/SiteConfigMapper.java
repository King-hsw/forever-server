package com.forever.server.setting;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SiteConfigMapper {

    @Select("SELECT * FROM sys_site_config ORDER BY config_key")
    List<SiteConfig> findAll();

    @Select("SELECT * FROM sys_site_config WHERE config_key = #{key}")
    SiteConfig findByKey(@Param("key") String key);

    @Insert("""
            INSERT INTO sys_site_config (config_key, config_value) VALUES (#{key}, #{value})
            ON CONFLICT (config_key) DO UPDATE
                SET config_value = EXCLUDED.config_value,
                    updated_at = CURRENT_TIMESTAMP
            """)
    int upsert(@Param("key") String key, @Param("value") String value);
}

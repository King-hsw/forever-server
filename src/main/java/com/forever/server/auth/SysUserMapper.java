package com.forever.server.auth;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysUserMapper {

    @Select("SELECT * FROM sys_user WHERE username = #{username}")
    SysUser findByUsername(String username);

    @Select("SELECT COUNT(*) FROM sys_user")
    long countAll();

    @Insert("INSERT INTO sys_user (username, password, nickname) " +
            "VALUES (#{username}, #{password}, #{nickname})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SysUser user);
}

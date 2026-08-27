package com.forever.server.auth;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysUserMapper {

    SysUser findByUsername(String username);

    SysUser findById(Long id);

    long countAll();

    int insert(SysUser user);

    List<SysUser> listAll();

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    int updatePassword(@Param("id") Long id, @Param("password") String password);

    int updateNickname(@Param("id") Long id, @Param("nickname") String nickname);
}

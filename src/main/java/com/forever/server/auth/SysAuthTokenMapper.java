package com.forever.server.auth;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysAuthTokenMapper {

    void insert(SysAuthToken token);

    SysAuthToken findByAccessToken(@Param("accessToken") String accessTokenHash);

    SysAuthToken findByRefreshToken(@Param("refreshToken") String refreshTokenHash);

    int deleteById(@Param("id") Long id);

    int deleteByRefreshToken(@Param("refreshToken") String refreshTokenHash);

    /** 清理 refresh 已过期的死行，登录/换发时顺手执行 */
    void purgeExpired();
}

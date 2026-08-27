package com.forever.server.friendlink;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface FriendLinkMapper {

    int insert(FriendLink link);

    int update(FriendLink link);

    int deleteById(Long id);

    FriendLink findById(Long id);

    List<FriendLink> findAll();

    List<FriendLink> findApproved();

    long countBySiteUrl(String siteUrl);

    int approve(@Param("id") Long id, @Param("now") LocalDateTime now);

    int reject(@Param("id") Long id, String reason, LocalDateTime now);
}

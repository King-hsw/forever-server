package com.forever.server.friendlink;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface FriendLinkMapper {

    @Insert("""
            INSERT INTO friend_link (name, site_url, icon_url, description, contact, status)
            VALUES (#{name}, #{siteUrl}, #{iconUrl}, #{description}, #{contact}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(FriendLink link);

    @Update("""
            UPDATE friend_link
            SET name = #{name}, site_url = #{siteUrl}, icon_url = #{iconUrl},
                description = #{description}, status = #{status},
                reject_reason = #{rejectReason}
            WHERE id = #{id}
            """)
    int update(FriendLink link);

    @Delete("DELETE FROM friend_link WHERE id = #{id}")
    int deleteById(Long id);

    @Select("SELECT * FROM friend_link WHERE id = #{id}")
    FriendLink findById(Long id);

    @Select("SELECT * FROM friend_link ORDER BY status ASC, created_at DESC")
    List<FriendLink> findAll();

    @Select("SELECT * FROM friend_link WHERE status = 'APPROVED' ORDER BY created_at ASC")
    List<FriendLink> findApproved();

    @Select("SELECT COUNT(*) FROM friend_link WHERE site_url = #{siteUrl}")
    long countBySiteUrl(String siteUrl);

    @Update("UPDATE friend_link SET status = 'APPROVED', reject_reason = NULL, reviewed_at = #{now} WHERE id = #{id}")
    int approve(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Update("UPDATE friend_link SET status = 'REJECTED', reject_reason = #{reason}, reviewed_at = #{now} WHERE id = #{id}")
    int reject(@Param("id") Long id, String reason, LocalDateTime now);
}

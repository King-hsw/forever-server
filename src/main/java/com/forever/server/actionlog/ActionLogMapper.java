package com.forever.server.actionlog;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ActionLogMapper {

    @Insert("""
            INSERT INTO sys_action_log (username, method, path, status, ip, duration_ms)
            VALUES (#{username}, #{method}, #{path}, #{status}, #{ip}, #{durationMs})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ActionLog entry);

    @Select("""
            <script>
            SELECT * FROM sys_action_log
            <where>
                <if test="username != null">AND username = #{username}</if>
                <if test="path != null">AND path ILIKE '%' || #{path} || '%'</if>
            </where>
            ORDER BY id DESC
            LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<ActionLog> page(@Param("username") String username,
                         @Param("path") String path,
                         @Param("offset") int offset,
                         @Param("size") int size);

    @Select("""
            <script>
            SELECT COUNT(*) FROM sys_action_log
            <where>
                <if test="username != null">AND username = #{username}</if>
                <if test="path != null">AND path ILIKE '%' || #{path} || '%'</if>
            </where>
            </script>
            """)
    long count(@Param("username") String username, @Param("path") String path);
}

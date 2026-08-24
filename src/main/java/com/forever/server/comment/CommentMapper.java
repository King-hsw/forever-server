package com.forever.server.comment;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface CommentMapper {

    @Insert("""
            INSERT INTO comment (target_type, target_id, parent_id, root_id, nickname, email, site, content, status, ip)
            VALUES (#{targetType}, #{targetId}, #{parentId}, #{rootId}, #{nickname}, #{email},
                    #{site}, #{content}, #{status}, #{ip})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Comment comment);

    @Select("SELECT * FROM comment WHERE id = #{id}")
    Comment findById(Long id);

    // ---------- 公开端：只看已过审 ----------

    String ROOT_CONDITION = """
            WHERE target_type = #{targetType} AND target_id = #{targetId} AND parent_id IS NULL AND status = 'APPROVED'
            """;

    @Select("SELECT * FROM comment " + ROOT_CONDITION + " ORDER BY created_at DESC LIMIT #{size} OFFSET #{offset}")
    List<Comment> pageApprovedRoots(@Param("targetType") String targetType,
                                    @Param("targetId") long targetId,
                                    @Param("offset") int offset,
                                    @Param("size") int size);

    @Select("SELECT COUNT(*) FROM comment " + ROOT_CONDITION)
    long countApprovedRoots(@Param("targetType") String targetType,
                            @Param("targetId") long targetId);

    @Select("""
            <script>
            SELECT * FROM comment
            WHERE status = 'APPROVED' AND root_id IN
            <foreach collection='rootIds' item='rid' open='(' separator=',' close=')'>#{rid}</foreach>
            ORDER BY created_at ASC
            </script>
            """)
    List<Comment> listApprovedReplies(@Param("rootIds") List<Long> rootIds);

    // ---------- 管理端：全量 ----------

    @Select("""
            <script>
            SELECT * FROM comment
            <where>
                <if test='status != null'>status = #{status}</if>
                <if test='targetType != null'>AND target_type = #{targetType}</if>
            </where>
            ORDER BY created_at DESC LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<Comment> pageAdmin(@Param("status") String status,
                            @Param("targetType") String targetType,
                            @Param("offset") int offset,
                            @Param("size") int size);

    @Select("""
            <script>
            SELECT COUNT(*) FROM comment
            <where>
                <if test='status != null'>status = #{status}</if>
                <if test='targetType != null'>AND target_type = #{targetType}</if>
            </where>
            </script>
            """)
    long countAdmin(@Param("status") String status,
                    @Param("targetType") String targetType);

    @Update("UPDATE comment SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /** 删除评论及其楼中所有回复 */
    @Delete("DELETE FROM comment WHERE id = #{id} OR root_id = #{id}")
    int deleteWithReplies(Long id);
}

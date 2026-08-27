package com.forever.server.comment;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CommentMapper {

    int insert(Comment comment);

    Comment findById(Long id);

    List<Comment> findByIds(@Param("ids") List<Long> ids);

    // ---------- 公开端：只看已过审 ----------

    List<Comment> pageApprovedRoots(@Param("targetType") String targetType,
                                    @Param("targetId") long targetId,
                                    @Param("offset") int offset,
                                    @Param("size") int size);

    long countApprovedRoots(@Param("targetType") String targetType,
                            @Param("targetId") long targetId);

    List<Comment> listApprovedReplies(@Param("rootIds") List<Long> rootIds);

    // ---------- 管理端：全量 ----------

    List<Comment> pageAdmin(@Param("status") String status,
                            @Param("targetType") String targetType,
                            @Param("offset") int offset,
                            @Param("size") int size);

    long countAdmin(@Param("status") String status,
                    @Param("targetType") String targetType);

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /** 删除评论及其楼中所有回复 */
    int deleteWithReplies(Long id);
}

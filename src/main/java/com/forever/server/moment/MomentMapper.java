package com.forever.server.moment;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface MomentMapper {

    int insert(Moment moment);

    Moment findById(Long id);

    List<Moment> page(@Param("uid") Long uid,
                      @Param("offset") int offset,
                      @Param("size") int size);

    long count(@Param("uid") Long uid);

    int deleteById(Long id);

    /** 批量取已过审评论数（MOMENT 评论共用 comment 表） */
    List<Map<String, Object>> commentCountsByMomentIds(@Param("momentIds") List<Long> momentIds);
}

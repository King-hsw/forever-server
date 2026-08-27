package com.forever.server.actionlog;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ActionLogMapper {

    int insert(ActionLog entry);

    List<ActionLog> page(@Param("username") String username,
                         @Param("path") String path,
                         @Param("offset") int offset,
                         @Param("size") int size);

    long count(@Param("username") String username, @Param("path") String path);
}

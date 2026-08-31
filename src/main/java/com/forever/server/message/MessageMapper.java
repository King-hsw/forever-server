package com.forever.server.message;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MessageMapper {

    int insert(Message message);

    List<Message> pageByUser(@Param("userId") long userId,
                             @Param("offset") int offset,
                             @Param("size") int size);

    long countByUser(@Param("userId") long userId);

    long countUnread(@Param("userId") long userId);

    int markRead(@Param("id") long id, @Param("userId") long userId);

    int markAllRead(@Param("userId") long userId);

    int softDelete(@Param("id") long id, @Param("userId") long userId);
}

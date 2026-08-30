package com.forever.server.push;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PushSubscriptionMapper {

    /** 按 endpoint 幂等 upsert：刷新密钥与归属用户，保留首订时间与历史推送时间 */
    int upsert(PushSubscription subscription);

    int deleteByEndpoint(@Param("endpoint") String endpoint);

    List<PushSubscription> findAll();

    /** 已确认送达（SW 回执过）的订阅数与最近一次回执时间 */
    long countDelivered();

    LocalDateTime maxDeliveredAt();

    int markDelivered(@Param("endpoint") String endpoint, @Param("now") LocalDateTime now);

    int markSent(@Param("id") Long id, @Param("now") LocalDateTime now);
}

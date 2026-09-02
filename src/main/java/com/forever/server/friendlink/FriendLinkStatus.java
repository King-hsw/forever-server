package com.forever.server.friendlink;

/**
 * 友链状态：待审核 / 已通过（前台可见）/ 已驳回。
 */
public enum FriendLinkStatus {
    /**
     * 待审核
     */
    PENDING,
    /**
     * 已通过，前台展示
     */
    APPROVED,
    /**
     * 已驳回
     */
    REJECTED
}

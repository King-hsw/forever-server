package com.forever.server.common;

/**
 * 分页参数解析与兜底（公开/管理接口共用）
 */
public final class PageParams {

    /**
     * size 上限，防大查询
     */
    public static final int MAX_SIZE = 100;

    private PageParams() {
    }

    /**
     * size 夹在 [1, MAX_SIZE]
     */
    public static int normalizeSize(int size) {
        return Math.min(Math.max(size, 1), MAX_SIZE);
    }

    /**
     * page 从 1 开始
     */
    public static int normalizePage(int page) {
        return Math.max(page, 1);
    }
}

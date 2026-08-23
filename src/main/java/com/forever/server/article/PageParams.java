package com.forever.server.article;

/** 分页参数解析与兜底（公开/管理接口共用） */
final class PageParams {

    private PageParams() {
    }

    static final int MAX_SIZE = 50;

    /** page 从 1 开始；size 上限 50，防大查询 */
    static int normalizeSize(int size) {
        return Math.min(Math.max(size, 1), MAX_SIZE);
    }

    static ArticleStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return ArticleStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null; // 非法值忽略筛选，不报错
        }
    }
}

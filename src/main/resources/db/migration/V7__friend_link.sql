-- V7 : 友情链接
-- 访客可提交友链申请（status = PENDING），管理员审核通过后在前台展示

CREATE TABLE friend_link (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,         -- 站点名称
    site_url      VARCHAR(500) NOT NULL UNIQUE,  -- 站点地址（申请与管理端全量更新的去重键）
    icon_url      VARCHAR(500),                  -- 站点图标/头像，前台展示用
    description   VARCHAR(200),                  -- 一句话简介
    contact       VARCHAR(200),                  -- 申请者联系方式（邮箱等），仅管理端可见
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING', -- PENDING / APPROVED / REJECTED
    reject_reason VARCHAR(200),                  -- 驳回原因（可选），仅管理端可见
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at   TIMESTAMP                      -- 审核时间；未审核为 null
);

-- V15 : 完整 RBAC 权限体系
-- sys_user 保持不变，用户-角色多对多；
-- 角色与权限均有主数据表，后台可调配「角色 -> 权限」矩阵。

CREATE TABLE sys_role (
    id         BIGSERIAL PRIMARY KEY,
    code       VARCHAR(30)  NOT NULL UNIQUE,
    name       VARCHAR(50)  NOT NULL,
    remark     VARCHAR(200),
    built_in   BOOLEAN      NOT NULL DEFAULT false,   -- 内置角色不可删除
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sys_permission (
    id         BIGSERIAL PRIMARY KEY,
    code       VARCHAR(50)  NOT NULL UNIQUE,           -- 如 moment:post，代码中引用
    name       VARCHAR(50)  NOT NULL,
    module     VARCHAR(30)  NOT NULL DEFAULT '其他',    -- 后台分组展示用
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 无外键约束：关联完整性由应用层维护（删角色/用户时手动清理关联）
CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE sys_role_permission (
    role_id       BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id)
);

-- 内置角色
INSERT INTO sys_role (code, name, remark, built_in) VALUES
    ('ADMIN',  '管理员', '站长，拥有全部权限', true),
    ('MEMBER', '成员',   '可发表朋友圈，可访问书城', true),
    ('USER',   '普通用户', '可访问书城', true);

-- 权限点（后续新功能在此追加种子行）
INSERT INTO sys_permission (code, name, module) VALUES
    ('admin:access',  '后台访问',   '系统'),
    ('moment:post',   '发表朋友圈', '朋友圈'),
    ('store:access',  '书城访问',   '书城'),
    ('store:manage',  '书城管理',   '书城');

-- 默认授权矩阵
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON
    (r.code = 'ADMIN' AND p.code IN ('admin:access', 'moment:post', 'store:access', 'store:manage'))
 OR (r.code = 'MEMBER' AND p.code IN ('moment:post', 'store:access'))
 OR (r.code = 'USER' AND p.code = 'store:access');

-- 存量管理员挂 ADMIN 角色（首次启动时若 sys_user 为空由 AdminInitializer 建号并授角色）
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u JOIN sys_role r ON r.code = 'ADMIN'
ON CONFLICT DO NOTHING;

ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

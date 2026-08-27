-- V19 : 权限粒度细化到端点（@Perm 注解）
-- 新权限码由 PermissionAutoRegistrar 启动时按注解自动注册，本文件不新增种子行。
-- 旧模块级 code 已被端点级 code 取代：从授权矩阵与权限表清理；
-- 引用旧 code 的自定义角色授权同步失效，需在后台重新勾选。
-- admin:access 保留不删（历史数据）。

DELETE FROM sys_role_permission
WHERE permission_id IN (
    SELECT id FROM sys_permission
    WHERE code IN (
        'article:manage', 'category:manage', 'tag:manage', 'comment:manage',
        'friend-link:manage', 'rss:manage', 'sensitive:manage', 'log:read',
        'setting:manage', 'rbac:manage'
    )
);

DELETE FROM sys_permission
WHERE code IN (
    'article:manage', 'category:manage', 'tag:manage', 'comment:manage',
    'friend-link:manage', 'rss:manage', 'sensitive:manage', 'log:read',
    'setting:manage', 'rbac:manage'
);

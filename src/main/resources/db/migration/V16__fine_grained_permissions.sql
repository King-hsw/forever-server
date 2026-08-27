-- V16 : 按接口模块拆分权限粒度
-- 各后台 Controller 通过 @PreAuthorize(hasAuthority('<code>')) 校验模块权限，
-- 角色在后台「权限管理」按需授予/收回；admin:access 不再作为后台总闸（历史数据保留不删）。

INSERT INTO sys_permission (code, name, module) VALUES
    ('article:manage',      '文章管理',   '内容'),
    ('category:manage',     '分类管理',   '内容'),
    ('tag:manage',          '标签管理',   '内容'),
    ('comment:manage',      '评论管理',   '互动'),
    ('friend-link:manage',  '友链管理',   '互动'),
    ('rss:manage',          'RSS 管理',  '订阅'),
    ('sensitive:manage',    '敏感词管理', '安全'),
    ('log:read',            '日志查看',   '系统'),
    ('setting:manage',      '站点设置',   '系统'),
    ('rbac:manage',         '角色权限',   '系统')
ON CONFLICT (code) DO NOTHING;

-- 新权限全部授予内置 ADMIN 角色，保证站长不受影响
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r JOIN sys_permission p ON p.code IN (
    'article:manage','category:manage','tag:manage','comment:manage','friend-link:manage',
    'rss:manage','sensitive:manage','log:read','setting:manage','rbac:manage'
) WHERE r.code = 'ADMIN'
ON CONFLICT DO NOTHING;

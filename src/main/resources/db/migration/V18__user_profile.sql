-- V18 : 用户资料字段（后台个人资料页：邮箱 / 自定义头像 / 个人主页）
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS email      VARCHAR(100);
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(255);
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS site       VARCHAR(200);

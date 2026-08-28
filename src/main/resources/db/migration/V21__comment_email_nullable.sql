-- V10 : 评论邮箱允许为空
-- 登录用户的动态评论取 sys_user 资料身份；资料未填邮箱时落库为空，
-- 头像回落昵称首字、不产生回复通知（游客发评仍要求邮箱，见 CommentService）
ALTER TABLE comment ALTER COLUMN email DROP NOT NULL;

-- V1 : 用户表（登录用）
CREATE TABLE sys_user (
    id         BIGSERIAL PRIMARY KEY,
    username   VARCHAR(50)  NOT NULL UNIQUE,
    password   VARCHAR(100) NOT NULL,
    nickname   VARCHAR(50),
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

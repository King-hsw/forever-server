-- V2 : 分类表
CREATE TABLE category (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(50)  NOT NULL UNIQUE,
    slug       VARCHAR(100) NOT NULL UNIQUE,
    sort       INT          NOT NULL DEFAULT 0,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

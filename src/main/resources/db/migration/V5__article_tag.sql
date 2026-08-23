-- V5 : 文章-标签多对多
CREATE TABLE article_tag (
    article_id BIGINT NOT NULL REFERENCES article(id),
    tag_id     BIGINT NOT NULL REFERENCES tag(id),
    PRIMARY KEY (article_id, tag_id)
);

CREATE INDEX idx_article_tag_tag ON article_tag(tag_id);

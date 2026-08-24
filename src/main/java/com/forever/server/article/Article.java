package com.forever.server.article;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class Article {

    private Long id;
    private String title;
    private String slug;
    private String summary;
    private String content;
    private String coverImage;
    private Long categoryId;
    /** 联表查询带回的分类名，非表字段 */
    private String categoryName;
    private ArticleStatus status;
    /** 内容类型：文章 / 独立页面 */
    private ArticleType type;
    /** 正文存储格式 */
    private ContentFormat contentFormat;
    private Long viewCount;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean deleted;
    /** 非表字段：文章的标签列表（service 组装） */
    private List<TagItem> tags = new ArrayList<>();

    /** 文章关联的标签项（联表查询结果） */
    public record TagItem(Long id, String name) {
    }

    /** 批量查标签时的联表投影行 */
    public record TagItemRow(Long articleId, Long tagId, String tagName) {
    }

    /** tags 不允许为 null，避免组装标签时 NPE */
    public void setTags(List<TagItem> tags) {
        this.tags = tags == null ? new ArrayList<>() : tags;
    }
}

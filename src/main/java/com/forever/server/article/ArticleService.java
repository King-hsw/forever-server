package com.forever.server.article;

import com.forever.server.category.CategoryMapper;
import com.forever.server.common.BizException;
import com.forever.server.common.ErrorCode;
import com.forever.server.common.PageResult;
import com.forever.server.common.SlugGenerator;
import com.forever.server.config.BlogProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ArticleService {

    private final ArticleMapper articleMapper;
    private final ArticleTagMapper articleTagMapper;
    private final CategoryMapper categoryMapper;
    private final com.forever.server.tag.TagMapper tagMapper;
    private final ApplicationEventPublisher events;
    private final BlogProperties props;

    public ArticleService(ArticleMapper articleMapper,
                          ArticleTagMapper articleTagMapper,
                          CategoryMapper categoryMapper,
                          com.forever.server.tag.TagMapper tagMapper,
                          ApplicationEventPublisher events,
                          BlogProperties props) {
        this.articleMapper = articleMapper;
        this.articleTagMapper = articleTagMapper;
        this.categoryMapper = categoryMapper;
        this.tagMapper = tagMapper;
        this.events = events;
        this.props = props;
    }

    // ---------- 管理端 ----------

    @Transactional
    public ArticleResponse create(ArticleSaveRequest request) {
        validateCategory(request.categoryId());
        validateTags(request.tagIds());
        Article article = new Article();
        applyRequest(article, request, resolveNewSlug(request.slug()));
        article.setStatus(ArticleStatus.DRAFT);
        articleMapper.insert(article);
        saveRelations(article.getId(), request.tagIds());
        log.info("article created: id={}, title={}, slug={}", article.getId(), article.getTitle(), article.getSlug());
        return getById(article.getId());
    }

    @Transactional
    public ArticleResponse update(Long id, ArticleSaveRequest request) {
        Article exists = requireExists(id);
        validateCategory(request.categoryId());
        validateTags(request.tagIds());
        // slug 变更才查冲突
        if (request.slug() != null && !request.slug().isBlank()
                && !request.slug().equals(exists.getSlug())) {
            if (articleMapper.existsBySlug(request.slug()) > 0) {
                throw new BizException(ErrorCode.CONFLICT, "slug 已被使用");
            }
            exists.setSlug(request.slug());
        }
        applyRequest(exists, request, exists.getSlug());
        articleMapper.update(exists);
        articleTagMapper.deleteByArticleId(id);
        saveRelations(id, request.tagIds());
        log.info("article updated: id={}, title={}", id, exists.getTitle());
        return getById(id);
    }

    public PageResult<ArticleResponse> pageAdmin(int page, int size,
                                                 ArticleStatus status,
                                                 String keyword, Long categoryId) {
        int offset = (page - 1) * size;
        List<Article> articles = articleMapper.adminPage(status, keyword, categoryId, offset, size);
        long total = articleMapper.countAdmin(status, keyword, categoryId);
        attachTags(articles);
        return PageResult.of(articles.stream().map(this::toResponse).toList(), total, page, size);
    }

    public ArticleResponse getById(Long id) {
        Article article = requireExists(id);
        attachTags(List.of(article));
        return toResponse(article);
    }

    @Transactional
    public void publish(Long id) {
        Article article = requireExists(id);
        articleMapper.publish(id);
        // 领域事件：搜索/AI/通知等扩展能力通过监听接入，Core 不感知具体订阅者
        events.publishEvent(new ArticlePublishedEvent(article.getId(), article.getSlug(), article.getTitle()));
        log.info("article published: id={}", id);
    }

    @Transactional
    public void unpublish(Long id) {
        Article article = requireExists(id);
        articleMapper.unpublish(id);
        events.publishEvent(new ArticleUnpublishedEvent(article.getId(), article.getSlug()));
        log.info("article unpublished: id={}", id);
    }

    @Transactional
    public void delete(Long id) {
        Article article = requireExists(id);
        articleMapper.softDelete(id); // 软删，关联与数据保留
        events.publishEvent(new ArticleDeletedEvent(article.getId(), article.getSlug()));
        log.info("article soft-deleted: id={}, title={}", id, article.getTitle());
    }

    // ---------- 公开端 ----------

    public PageResult<ArticleResponse> pagePublic(int page, int size,
                                                  String keyword, Long categoryId, Long tagId) {
        int offset = (page - 1) * size;
        List<Article> articles = articleMapper.publicPage(keyword, categoryId, tagId, offset, size);
        long total = articleMapper.countPublic(keyword, categoryId, tagId);
        attachTags(articles);
        return PageResult.of(articles.stream().map(this::toResponse).toList(), total, page, size);
    }

    @Transactional
    public ArticleResponse detailPublic(String slug) {
        Article article = articleMapper.findBySlug(slug);
        if (article == null || article.getStatus() != ArticleStatus.PUBLISHED) {
            log.debug("public article not found or not published: slug={}", slug);
            throw new BizException(ErrorCode.NOT_FOUND, "文章不存在");
        }
        articleMapper.incrementViewCount(article.getId()); // 详情访问即浏览量 +1
        article.setViewCount(article.getViewCount() == null ? 1 : article.getViewCount() + 1);
        attachTags(List.of(article));
        return toResponse(article);
    }

    // ---------- internal ----------

    Article requireExists(Long id) {
        Article article = articleMapper.findById(id);
        if (article == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "文章不存在");
        }
        return article;
    }

    private void applyRequest(Article article, ArticleSaveRequest request, String slug) {
        article.setTitle(request.title());
        article.setSlug(slug);
        article.setContent(request.content());
        article.setSummary(request.summary());
        article.setCoverImage(request.coverImage());
        article.setCategoryId(request.categoryId());
        article.setType(firstNonNull(request.type(), article.getType(), ArticleType.ARTICLE));
        article.setContentFormat(firstNonNull(request.contentFormat(), article.getContentFormat(), ContentFormat.MARKDOWN));
    }

    /** 请求值优先；否则保留实体已有值（更新场景）；都没有则用默认值（创建场景） */
    private static <T> T firstNonNull(T requested, T existing, T fallback) {
        if (requested != null) {
            return requested;
        }
        return existing != null ? existing : fallback;
    }

    private String resolveNewSlug(String requested) {
        if (requested != null && !requested.isBlank()) {
            if (articleMapper.existsBySlug(requested) > 0) {
                throw new BizException(ErrorCode.CONFLICT, "slug 已被使用");
            }
            return requested;
        }
        // 自动生成：时间戳 + 6 位随机串，冲突重试
        for (int i = 0; i < 3; i++) {
            String candidate = SlugGenerator.randomSlug();
            if (articleMapper.existsBySlug(candidate) == 0) {
                return candidate;
            }
        }
        throw new BizException(ErrorCode.CONFLICT, "slug 生成失败，请稍后重试");
    }

    private void validateCategory(Long categoryId) {
        if (categoryId != null && categoryMapper.findById(categoryId) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "分类不存在");
        }
    }

    private void validateTags(List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        for (Long tagId : tagIds) {
            if (tagMapper.findById(tagId) == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "标签不存在: " + tagId);
            }
        }
    }

    private void saveRelations(Long articleId, List<Long> tagIds) {
        if (tagIds == null) {
            return;
        }
        tagIds.stream().distinct().forEach(tagId -> articleTagMapper.insert(articleId, tagId));
    }

    /** 批量组装标签，避免 N+1 查询 */
    private void attachTags(List<Article> articles) {
        if (articles.isEmpty()) {
            return;
        }
        List<Long> ids = articles.stream().map(Article::getId).toList();
        Map<Long, List<Article.TagItem>> byArticle = articleMapper.tagRowsForArticles(ids).stream()
                .collect(Collectors.groupingBy(Article.TagItemRow::articleId,
                        Collectors.mapping(r -> new Article.TagItem(r.tagId(), r.tagName()),
                                Collectors.toList())));
        for (Article a : articles) {
            a.setTags(byArticle.getOrDefault(a.getId(), List.of()).stream()
                    .sorted(Comparator.comparing(Article.TagItem::id))
                    .toList());
        }
    }

    private ArticleResponse toResponse(Article a) {
        return new ArticleResponse(
                a.getId(), a.getTitle(), a.getSlug(), a.getSummary(), a.getContent(),
                a.getCoverImage(), a.getCategoryId(), a.getCategoryName(),
                a.getTags(), a.getStatus(), a.getType(), a.getContentFormat(),
                a.getViewCount() == null ? 0 : a.getViewCount(),
                a.getPublishedAt(), a.getCreatedAt(), a.getUpdatedAt(),
                publicUrl(a.getSlug()),
                a.getContent() == null ? null : ArticleResponse.estimateReadingTime(a.getContent()));
    }

    /** 前台完整 URL，供机器消费方直接使用；未配置 blog.site.url 时返回 null */
    private String publicUrl(String slug) {
        if (props.site() == null || props.site().url() == null || props.site().url().isBlank()) {
            return null;
        }
        String base = props.site().url();
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + "/articles/" + slug;
    }
}

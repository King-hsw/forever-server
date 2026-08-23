package com.forever.server.comment;

import com.forever.server.article.Article;
import com.forever.server.article.ArticleMapper;
import com.forever.server.article.ArticleStatus;
import com.forever.server.common.BizException;
import com.forever.server.common.ErrorCode;
import com.forever.server.common.PageResult;
import com.forever.server.config.BlogProperties;
import com.forever.server.sensitive.SensitiveWordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CommentService {

    /** 同 IP 发评间隔（毫秒） */
    private static final long POST_INTERVAL_MS = 60_000;

    private final CommentMapper commentMapper;
    private final ArticleMapper articleMapper;
    private final SensitiveWordService sensitiveWordService;
    private final ApplicationEventPublisher events;
    private final BlogProperties props;
    /** IP -> 上次发评时间，简单内存限流（单实例够用） */
    private final Map<String, LocalDateTime> lastPostByIp = new ConcurrentHashMap<>();

    public CommentService(CommentMapper commentMapper,
                          ArticleMapper articleMapper,
                          SensitiveWordService sensitiveWordService,
                          ApplicationEventPublisher events,
                          BlogProperties props) {
        this.commentMapper = commentMapper;
        this.articleMapper = articleMapper;
        this.sensitiveWordService = sensitiveWordService;
        this.events = events;
        this.props = props;
    }

    // ---------- 公开端 ----------

    /**
     * 分页取某篇文章的评论，组装成两层楼：根评论倒序，楼内回复正序。
     */
    public PageResult<CommentResponse> pageByArticle(Long articleId, int page, int size) {
        int offset = (page - 1) * size;
        List<Comment> roots = commentMapper.pageApprovedRoots(articleId, offset, size);
        long total = commentMapper.countApprovedRoots(articleId);

        List<CommentResponse> items;
        if (roots.isEmpty()) {
            items = List.of();
        } else {
            List<Long> rootIds = roots.stream().map(Comment::getId).toList();
            Map<Long, List<Comment>> repliesByRoot = commentMapper.listApprovedReplies(rootIds).stream()
                    .collect(Collectors.groupingBy(Comment::getRootId));
            items = roots.stream()
                    .map(root -> {
                        List<CommentResponse> replies = repliesByRoot
                                .getOrDefault(root.getId(), List.of()).stream()
                                .map(r -> CommentResponse.reply(r, avatarUrl(r.getEmail())))
                                .toList();
                        return new CommentResponse(root.getId(), root.getNickname(),
                                avatarUrl(root.getEmail()), root.getSite(), root.getContent(),
                                root.getCreatedAt(), replies);
                    })
                    .toList();
        }
        return PageResult.of(items, total, page, size);
    }

    /**
     * 发表评论。流程：限流 -> 校验文章 -> 组装层级 -> 敏感词打码 ->
     * 按 auto-approve 决定状态 -> 落库 -> 发出 CommentCreatedEvent（邮件等由监听者处理）。
     */
    public CommentAdminResponse create(CommentCreateRequest request, String ip) {
        throttle(ip);

        Article article = articleMapper.findById(request.articleId());
        if (article == null || article.getStatus() != ArticleStatus.PUBLISHED) {
            throw new BizException(ErrorCode.NOT_FOUND, "文章不存在");
        }

        Comment parent = null;
        if (request.parentId() != null) {
            parent = requireVisible(request.parentId());
        }

        Comment comment = new Comment();
        comment.setArticleId(request.articleId());
        comment.setParentId(parent == null ? null : parent.getId());
        comment.setRootId(parent == null ? null
                : (parent.getRootId() != null ? parent.getRootId() : parent.getId()));
        comment.setNickname(request.nickname().trim());
        comment.setEmail(request.email().trim());
        comment.setSite(blankToNull(request.site()));
        comment.setContent(sensitiveWordService.mask(request.content().trim()));

        boolean autoApprove = props.comment() == null || props.comment().autoApprove();
        comment.setStatus(autoApprove ? "APPROVED" : "PENDING");
        comment.setIp(ip);

        try {
            commentMapper.insert(comment);
        } catch (DataAccessException e) {
            // parent 被并发删除等极端情况，外键冲突兜底为友好错误
            log.warn("comment insert rejected: articleId={}, reason={}", request.articleId(), e.getMessage());
            throw new BizException(ErrorCode.CONFLICT, "评论提交失败，请刷新后重试");
        }
        log.info("comment created: id={}, articleId={}, parentId={}, status={}, ip={}",
                comment.getId(), comment.getArticleId(), comment.getParentId(), comment.getStatus(), ip);

        // 通知等后续动作与评论主流程解耦：监听失败不影响已落库的评论
        events.publishEvent(new CommentCreatedEvent(comment, parent, article.getTitle()));
        return toAdminResponse(comment);
    }

    // ---------- 管理端 ----------

    public PageResult<CommentAdminResponse> pageAdmin(String status, int page, int size) {
        int offset = (page - 1) * size;
        List<Comment> comments = commentMapper.pageAdmin(status, offset, size);
        long total = commentMapper.countAdmin(status);
        return PageResult.of(comments.stream().map(this::toAdminResponse).toList(), total, page, size);
    }

    public void approve(Long id) {
        updateStatus(id, "APPROVED");
    }

    public void reject(Long id) {
        updateStatus(id, "REJECTED");
    }

    public void delete(Long id) {
        requireExists(id);
        commentMapper.deleteWithReplies(id);
        log.info("comment deleted with replies: id={}", id);
    }

    // ---------- internal ----------

    private void throttle(String ip) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime last = lastPostByIp.put(ip, now);
        if (last != null && last.isAfter(now.minusNanos(POST_INTERVAL_MS * 1_000_000))) {
            throw new BizException(ErrorCode.CONFLICT, "评论太频繁，请稍后再试");
        }
        // 防止 map 无限增长
        if (lastPostByIp.size() > 10_000) {
            lastPostByIp.entrySet().removeIf(e -> e.getValue().isBefore(now.minusHours(1)));
        }
    }

    private Comment requireVisible(Long id) {
        Comment parent = commentMapper.findById(id);
        if (parent == null || !"APPROVED".equals(parent.getStatus())) {
            throw new BizException(ErrorCode.NOT_FOUND, "被回复的评论不存在或未通过审核");
        }
        return parent;
    }

    private void updateStatus(Long id, String status) {
        requireExists(id);
        commentMapper.updateStatus(id, status);
        log.info("comment {}: id={}", status.toLowerCase(), id);
    }

    private Comment requireExists(Long id) {
        Comment comment = commentMapper.findById(id);
        if (comment == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "评论不存在");
        }
        return comment;
    }

    private CommentAdminResponse toAdminResponse(Comment c) {
        Article article = articleMapper.findById(c.getArticleId());
        return new CommentAdminResponse(c.getId(), c.getArticleId(),
                article != null ? article.getTitle() : null,
                c.getParentId(), c.getRootId(),
                c.getNickname(), c.getEmail(), c.getSite(), c.getContent(),
                c.getStatus(), c.getIp(), c.getCreatedAt());
    }

    /** 头像：邮箱 MD5 -> Cravatar（Gravatar 国内镜像） */
    static String avatarUrl(String email) {
        String hash = DigestUtils.md5DigestAsHex(
                email.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
        return "https://cravatar.cn/avatar/" + hash + "?d=mp&s=80";
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

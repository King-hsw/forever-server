package com.forever.server.comment;

import com.forever.server.article.Article;
import com.forever.server.article.ArticleMapper;
import com.forever.server.article.ArticleStatus;
import com.forever.server.common.BizException;
import com.forever.server.common.ErrorCode;
import com.forever.server.common.PageResult;
import com.forever.server.config.BlogProperties;
import com.forever.server.sensitive.SensitiveWordService;
import com.forever.server.setting.SiteConfigService;
import lombok.extern.slf4j.Slf4j;
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

    /** 同 IP 发评最小间隔（秒），可通过 blog.comment.post-interval-seconds 覆盖 */
    private static final long DEFAULT_POST_INTERVAL_SECONDS = 10;

    static final String TARGET_ARTICLE = "ARTICLE";
    static final String TARGET_BOARD = "BOARD";

    private final CommentMapper commentMapper;
    private final ArticleMapper articleMapper;
    private final SensitiveWordService sensitiveWordService;
    private final SiteConfigService siteConfig;
    private final CommentNotifyService notifyService;
    /** IP -> 上次发评时间，简单内存限流（单实例够用） */
    private final Map<String, LocalDateTime> lastPostByIp = new ConcurrentHashMap<>();

    public CommentService(CommentMapper commentMapper,
                          ArticleMapper articleMapper,
                          SensitiveWordService sensitiveWordService,
                          SiteConfigService siteConfig,
                          CommentNotifyService notifyService) {
        this.commentMapper = commentMapper;
        this.articleMapper = articleMapper;
        this.sensitiveWordService = sensitiveWordService;
        this.siteConfig = siteConfig;
        this.notifyService = notifyService;
    }

    // ---------- 公开端 ----------

    /**
     * 分页取某篇文章的评论，组装成两层楼：根评论倒序，楼内回复正序。
     */
    public PageResult<CommentResponse> pageByArticle(Long articleId, int page, int size) {
        return pageRoots(TARGET_ARTICLE, articleId, page, size);
    }

    /** 留言板分页（BOARD 评论全部挂在固定 target_id = 0 上） */
    public PageResult<CommentResponse> pageByBoard(int page, int size) {
        return pageRoots(TARGET_BOARD, 0L, page, size);
    }

    private PageResult<CommentResponse> pageRoots(String targetType, long targetId, int page, int size) {
        int offset = (page - 1) * size;
        List<Comment> roots = commentMapper.pageApprovedRoots(targetType, targetId, offset, size);
        long total = commentMapper.countApprovedRoots(targetType, targetId);

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
     * 发表文章评论。流程见 {@link #doCreate}。
     */
    public CommentAdminResponse create(CommentCreateRequest request, String ip) {
        Article article = articleMapper.findById(request.articleId());
        if (article == null || article.getStatus() != ArticleStatus.PUBLISHED) {
            throw new BizException(ErrorCode.NOT_FOUND, "文章不存在");
        }
        return doCreate(TARGET_ARTICLE, request.articleId(), article.getTitle(), request, ip);
    }

    /** 发表留言板留言（不关联文章） */
    public CommentAdminResponse createBoard(CommentCreateRequest request, String ip) {
        return doCreate(TARGET_BOARD, 0L, siteConfig.boardTitle(), request, ip);
    }

    private CommentAdminResponse doCreate(String targetType, long targetId, String sourceTitle,
                                          CommentCreateRequest request, String ip) {
        throttle(ip);

        Comment parent = null;
        if (request.parentId() != null) {
            parent = requireVisible(request.parentId());
        }

        Comment comment = new Comment();
        comment.setTargetType(targetType);
        comment.setTargetId(targetId);
        comment.setParentId(parent == null ? null : parent.getId());
        comment.setRootId(parent == null ? null
                : (parent.getRootId() != null ? parent.getRootId() : parent.getId()));
        comment.setNickname(request.nickname().trim());
        comment.setEmail(request.email().trim());
        comment.setSite(blankToNull(request.site()));
        comment.setContent(sensitiveWordService.mask(request.content().trim()));

        boolean autoApprove = siteConfig.getBoolean(SiteConfigService.COMMENT_AUTO_APPROVE, true);
        comment.setStatus(autoApprove ? "APPROVED" : "PENDING");
        comment.setIp(ip);

        try {
            commentMapper.insert(comment);
        } catch (DataAccessException e) {
            // parent 被并发删除等极端情况，外键冲突兜底为友好错误
            log.warn("comment insert rejected: targetType={}, reason={}", targetType, e.getMessage());
            throw new BizException(ErrorCode.CONFLICT, "评论提交失败，请刷新后重试");
        }
        log.info("comment created: id={}, targetType={}, parentId={}, status={}, ip={}",
                comment.getId(), targetType, comment.getParentId(), comment.getStatus(), ip);

        // 通知失败不影响已落库的评论（notify 内部自捕获异常）
        notifyService.onCommentCreated(comment, parent, sourceTitle);
        return toAdminResponse(comment);
    }

    // ---------- 管理端 ----------

    public PageResult<CommentAdminResponse> pageAdmin(String status, String targetType, int page, int size) {
        int offset = (page - 1) * size;
        List<Comment> comments = commentMapper.pageAdmin(status, targetType, offset, size);
        long total = commentMapper.countAdmin(status, targetType);
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
        long intervalMs = postIntervalMs();
        if (intervalMs <= 0) {
            return; // 0 = 不限流
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime last = lastPostByIp.get(ip);
        if (last != null && last.isAfter(now.minusNanos(intervalMs * 1_000_000))) {
            throw new BizException(ErrorCode.CONFLICT, "评论太频繁，请稍后再试");
        }
        // 通过校验才记录本次时间，避免被拒请求反复刷新窗口导致一直发不出去
        lastPostByIp.put(ip, now);
        // 防止 map 无限增长
        if (lastPostByIp.size() > 10_000) {
            lastPostByIp.entrySet().removeIf(e -> e.getValue().isBefore(now.minusHours(1)));
        }
    }

    /** 生效间隔：后台站点设置优先，未设置时用内置默认值；0 表示不限流 */
    private long postIntervalMs() {
        return Math.max(0, siteConfig.getLong(
                SiteConfigService.COMMENT_POST_INTERVAL_SECONDS, DEFAULT_POST_INTERVAL_SECONDS)) * 1000;
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
        String title;
        if (TARGET_BOARD.equals(c.getTargetType())) {
            title = null; // 前端按 targetType 显示「留言板」
        } else {
            Article article = articleMapper.findById(c.getTargetId());
            title = article != null ? article.getTitle() : null;
        }
        return new CommentAdminResponse(c.getId(), c.getTargetType(), title,
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

package com.forever.server.comment;

import com.forever.server.article.Article;
import com.forever.server.article.ArticleMapper;
import com.forever.server.article.ArticleStatus;
import com.forever.server.auth.SysUser;
import com.forever.server.auth.SysUserMapper;
import com.forever.server.common.BizException;
import com.forever.server.common.ErrorCode;
import com.forever.server.common.PageResult;
import com.forever.server.common.Strings;
import com.forever.server.moment.MomentMapper;
import com.forever.server.sensitive.SensitiveWordService;
import com.forever.server.setting.SiteConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 评论核心业务：一套 Comment 表支撑三种目标（target_type = ARTICLE / BOARD / MOMENT），
 * 组装两层楼（根评论倒序、楼内回复正序）。游客发评要求昵称+邮箱，登录用户在动态下
 * 自动以 sys_user 资料身份发言；写入前做敏感词替换（{@link SensitiveWordService#mask}），
 * 是否直接过审、同 IP 发评间隔均由站点设置控制；落库后发布 CommentCreatedEvent
 * （邮件 / Web Push / 站内消息等渠道各自订阅，失败不影响评论）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentService {

    /**
     * 同 IP 发评最小间隔（秒），可通过 blog.comment.post-interval-seconds 覆盖
     */
    private static final long DEFAULT_POST_INTERVAL_SECONDS = 10;

    static final String TARGET_ARTICLE = "ARTICLE";
    static final String TARGET_BOARD = "BOARD";
    /**
     * MOMENT 目标供 moment 包引用
     */
    public static final String TARGET_MOMENT = "MOMENT";

    /**
     * 留言板标题与前台路径：已写死，不再走站点设置（board.title / board.summary 已移除）。
     * 标题会出现在站内消息文案里（「《留言板》收到新评论」）；
     * 前端 chat 页的聊天室标题与 SEO 标题也硬编码了同一个词，改这里要同步改前端。
     */
    private static final String BOARD_TITLE = "留言板";
    private static final String BOARD_URL = "/chat";

    private final CommentMapper commentMapper;
    private final ArticleMapper articleMapper;
    private final MomentMapper momentMapper;
    private final SensitiveWordService sensitiveWordService;
    private final SiteConfigService siteConfig;
    private final SysUserMapper sysUserMapper;
    private final ApplicationEventPublisher eventPublisher;
    /**
     * IP -> 上次发评时间，简单内存限流（单实例够用）
     */
    private final Map<String, LocalDateTime> lastPostByIp = new ConcurrentHashMap<>();

    // ---------- 公开端 ----------

    /**
     * 分页取某篇文章的评论，组装成两层楼：根评论倒序，楼内回复正序。
     */
    public PageResult<CommentResponse> pageByArticle(Long articleId, int page, int size) {
        return pageRoots(TARGET_ARTICLE, articleId, page, size);
    }

    /**
     * 留言板分页（BOARD 评论全部挂在固定 target_id = 0 上）
     */
    public PageResult<CommentResponse> pageByBoard(int page, int size) {
        return pageRoots(TARGET_BOARD, 0L, page, size);
    }

    /**
     * 动态评论分页（MOMENT 评论挂在 target_id = 动态 id 上）
     */
    public PageResult<CommentResponse> pageByMoment(Long momentId, int page, int size) {
        return pageRoots(TARGET_MOMENT, momentId, page, size);
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

            // 楼内回复再回复楼内回复时，取被回复的父评论供前端展示引用（仅展示已过审的）
            Set<Long> parentIds = new HashSet<>();
            repliesByRoot.forEach((rootId, replies) -> replies.stream()
                    .map(Comment::getParentId)
                    .filter(pid -> pid != null && !pid.equals(rootId))
                    .forEach(parentIds::add));
            Map<Long, Comment> parents = parentIds.isEmpty() ? Map.of()
                    : commentMapper.findByIds(new ArrayList<>(parentIds)).stream()
                    .filter(c -> "APPROVED".equals(c.getStatus()))
                    .collect(Collectors.toMap(Comment::getId, c -> c));

            items = roots.stream()
                    .map(root -> {
                        List<CommentResponse> replies = repliesByRoot
                                .getOrDefault(root.getId(), List.of()).stream()
                                .map(r -> {
                                    Comment p = parents.get(r.getParentId());
                                    return CommentResponse.reply(r, Strings.gravatarUrl(r.getEmail()),
                                            p == null ? null : p.getNickname(),
                                            p == null ? null : p.getContent());
                                })
                                .toList();
                        return new CommentResponse(root.getId(), null, root.getNickname(),
                                Strings.gravatarUrl(root.getEmail()), root.getSite(), root.getContent(),
                                root.getCreatedAt(), replies, null, null);
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
        return doCreate(TARGET_ARTICLE, request.articleId(), article.getTitle(), "/posts/" + article.getSlug(),
                request, ip, visitorIdentity(request));
    }

    /**
     * 发表留言板留言（不关联文章）
     */
    public CommentAdminResponse createBoard(CommentCreateRequest request, String ip) {
        return doCreate(TARGET_BOARD, 0L, BOARD_TITLE, BOARD_URL,
                request, ip, visitorIdentity(request));
    }

    /**
     * 发表动态评论：登录用户（viewerUid 非空）自动以其 sys_user 资料身份发布，邮箱可为空
     */
    public CommentAdminResponse createMoment(Long momentId, Long viewerUid, CommentCreateRequest request, String ip) {
        if (momentMapper.findById(momentId) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "动态不存在");
        }
        Identity identity = viewerUid != null ? userIdentity(viewerUid) : visitorIdentity(request);
        return doCreate(TARGET_MOMENT, momentId, "动态 #" + momentId, "/moments",
                request, ip, identity);
    }

    private CommentAdminResponse doCreate(String targetType, long targetId, String sourceTitle, String sourceUrl,
                                          CommentCreateRequest request, String ip, Identity identity) {
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
        comment.setUserId(identity.uid());
        comment.setNickname(identity.nickname());
        comment.setEmail(identity.email());
        comment.setSite(identity.site());
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

        // 通知渠道各自订阅并自捕获异常，失败不影响已落库的评论
        eventPublisher.publishEvent(new CommentCreatedEvent(comment, parent, sourceTitle, sourceUrl));
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

    /**
     * 发言身份：归属账号 uid（游客为空）+ 昵称 + 邮箱（可为空，登录用户资料未填时）+ 主页
     */
    private record Identity(Long uid, String nickname, String email, String site) {
    }

    /**
     * 游客身份：取自请求体；DB 允许邮箱为空（登录用户），游客仍显式要求
     */
    private Identity visitorIdentity(CommentCreateRequest request) {
        if (request.email() == null || request.email().isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "邮箱不能为空");
        }
        return new Identity(null, request.nickname().trim(), request.email().trim(), Strings.blankToNull(request.site()));
    }

    /**
     * 登录用户身份：取自 sys_user，忽略请求体携带的身份字段（昵称缺省回落用户名）
     */
    private Identity userIdentity(Long uid) {
        SysUser user = sysUserMapper.findById(uid);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "登录用户不存在");
        }
        String nickname = Strings.blankToNull(user.getNickname());
        return new Identity(uid, nickname != null ? nickname.trim() : user.getUsername(),
                Strings.blankToNull(user.getEmail()), Strings.blankToNull(user.getSite()));
    }

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

    /**
     * 生效间隔：后台站点设置优先，未设置时用内置默认值；0 表示不限流
     */
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
        if (TARGET_MOMENT.equals(c.getTargetType())) {
            title = "动态 #" + c.getTargetId();
        } else if (TARGET_BOARD.equals(c.getTargetType())) {
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
}

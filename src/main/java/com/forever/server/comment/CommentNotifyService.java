package com.forever.server.comment;

import com.forever.server.auth.SysUser;
import com.forever.server.auth.SysUserMapper;
import com.forever.server.common.Strings;
import com.forever.server.config.BlogProperties;
import com.forever.server.mail.MailService;
import com.forever.server.push.PushService;
import com.forever.server.setting.SiteConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * 评论通知：邮件与 Web Push 双通道，同一开关（comment.notifyMail）控制。
 * 设计原则：通知失败绝不影响评论本身——
 * 未开启开关、未配置 SMTP/VAPID、发送异常都只记日志。
 */
@Slf4j
@Service
public class CommentNotifyService {

    private final MailService mailService;
    private final SiteConfigService siteConfig;
    private final PushService pushService;
    private final SysUserMapper sysUserMapper;
    private final BlogProperties blogProperties;

    public CommentNotifyService(MailService mailService, SiteConfigService siteConfig,
                                PushService pushService, SysUserMapper sysUserMapper, BlogProperties blogProperties) {
        this.mailService = mailService;
        this.siteConfig = siteConfig;
        this.pushService = pushService;
        this.sysUserMapper = sysUserMapper;
        this.blogProperties = blogProperties;
    }

    /**
     * 评论落库后的通知（邮件 + Web Push）：
     * - 回复他人 -> 通知被回复者（邮件按其邮箱；推送命中以该邮箱绑定的订阅）
     * - 新的根评论 -> 通知站长（邮件按 owner-email 配置；推送按站长账号名下的订阅）
     */
    @EventListener
    public void onCommentCreated(CommentCreatedEvent event) {
        Comment comment = event.comment();
        Comment parent = event.parent();
        String articleTitle = event.sourceTitle();
        String sourceUrl = event.sourceUrl();
        if (!enabled()) {
            return;
        }
        try {
            if (parent != null) {
                String summary = "你在《%s》下的评论收到了 %s 的回复：%s".formatted(
                        articleTitle, comment.getNickname(), Strings.excerpt(comment.getContent(), 80));
                send(parent.getEmail(), "你的评论收到了新回复", buildReplyBody(parent, comment, articleTitle));
                pushService.sendToEmail(parent.getEmail(), "你的评论收到了新回复", summary, sourceUrl);
            } else if (ownerEmail() != null && !ownerEmail().equalsIgnoreCase(comment.getEmail())) {
                String summary = "《%s》收到新评论：%s：%s".formatted(
                        articleTitle, comment.getNickname(), Strings.excerpt(comment.getContent(), 80));
                send(ownerEmail(), "博客有新的评论",
                        """
                        《%s》收到新评论：

                        昵称：%s
                        内容：%s

                        请登录后台查看与回复。""".formatted(articleTitle, comment.getNickname(), comment.getContent()));
                Long ownerUid = ownerUid();
                if (ownerUid != null) {
                    pushService.sendToUser(ownerUid, "博客有新的评论", summary, sourceUrl);
                }
            }
        } catch (Exception e) {
            log.warn("comment notify failed: commentId={}, reason={}", comment.getId(), e.getMessage());
        }
    }

    /** 站长账号 uid（按启动配置的管理员用户名查）；配置缺失或账号不存在返回 null，推送自然跳过 */
    private Long ownerUid() {
        if (blogProperties.admin() == null || blogProperties.admin().username() == null) {
            return null;
        }
        SysUser owner = sysUserMapper.findByUsername(blogProperties.admin().username());
        return owner == null ? null : owner.getId();
    }

    private boolean enabled() {
        return siteConfig.getBoolean(SiteConfigService.COMMENT_NOTIFY_MAIL, false);
    }

    private String ownerEmail() {
        return siteConfig.getString(SiteConfigService.COMMENT_OWNER_EMAIL, null);
    }

    private String buildReplyBody(Comment parent, Comment reply, String articleTitle) {
        return """
                你在《%s》下的评论：

                  %s

                收到了 %s 的回复：

                  %s""".formatted(articleTitle, parent.getContent(), reply.getNickname(), reply.getContent());
    }

    private void send(String to, String subject, String text) {
        if (to == null || to.isBlank()) {
            return; // 收件人未留邮箱（登录用户资料无邮箱），不发
        }
        if (!mailService.configured()) {
            log.debug("mail skipped: mail.* not configured");
            return;
        }
        mailService.send(to, subject, text);
    }
}

package com.forever.server.comment;

import com.forever.server.auth.SysUser;
import com.forever.server.auth.SysUserMapper;
import com.forever.server.common.Strings;
import com.forever.server.config.BlogProperties;
import com.forever.server.mail.MailService;
import com.forever.server.push.PushService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * 评论通知：邮件与 Web Push 双通道，通知常开（无开关）：
 * 任何评论（根/回复）只要评论者不是站长收件人（blog.mail.notify-to）就通知站长；
 * 回复额外通知被回复者（其本人即站长时跳过，不重复）。
 * 设计原则：通知失败绝不影响评论本身——
 * 未配置 SMTP/VAPID、发送异常都只记日志。
 */
@Slf4j
@Service
public class CommentNotifyService {

    private final MailService mailService;
    private final PushService pushService;
    private final SysUserMapper sysUserMapper;
    private final BlogProperties blogProperties;
    /** 站长收件人（env BLOG_MAIL_NOTIFY_TO 必填） */
    private final String notifyTo;

    public CommentNotifyService(MailService mailService,
                               PushService pushService,
                               SysUserMapper sysUserMapper,
                               BlogProperties blogProperties,
                               @Value("${blog.mail.notify-to}") String notifyTo) {
        this.mailService = mailService;
        this.pushService = pushService;
        this.sysUserMapper = sysUserMapper;
        this.blogProperties = blogProperties;
        this.notifyTo = notifyTo;
    }

    /**
     * 评论落库后的通知（邮件 + Web Push）：
     * - 根评论/回复 -> 站长（notify-to 收件；推送按站长账号名下的订阅），评论者即站长时零通知
     * - 回复额外 -> 被回复者（邮件按其邮箱；推送命中以该邮箱绑定的订阅），被回复者即站长时跳过
     */
    @EventListener
    public void onCommentCreated(CommentCreatedEvent event) {
        Comment comment = event.comment();
        Comment parent = event.parent();
        String articleTitle = event.sourceTitle();
        String sourceUrl = event.sourceUrl();
        try {
            if (!isNotifyTo(comment.getEmail())) {
                String summary = "《%s》收到新评论：%s：%s".formatted(
                        articleTitle, comment.getNickname(), Strings.excerpt(comment.getContent(), 80));
                send(notifyTo, "博客有新的评论",
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
            if (parent != null && !isNotifyTo(parent.getEmail())) {
                String summary = "你在《%s》下的评论收到了 %s 的回复：%s".formatted(
                        articleTitle, comment.getNickname(), Strings.excerpt(comment.getContent(), 80));
                send(parent.getEmail(), "你的评论收到了新回复", buildReplyBody(parent, comment, articleTitle));
                pushService.sendToEmail(parent.getEmail(), "你的评论收到了新回复", summary, sourceUrl);
            }
        } catch (Exception e) {
            log.warn("comment notify failed: commentId={}, reason={}", comment.getId(), e.getMessage());
        }
    }

    /**
     * 站长账号 uid（按启动配置的管理员用户名查）；配置缺失或账号不存在返回 null，推送自然跳过
     */
    private Long ownerUid() {
        if (blogProperties.admin() == null || blogProperties.admin().username() == null) {
            return null;
        }
        SysUser owner = sysUserMapper.findByUsername(blogProperties.admin().username());
        return owner == null ? null : owner.getId();
    }

    /**
     * 是否站长收件人（null 安全：notifyTo 必填非空，email 为 null 时返回 false）
     */
    private boolean isNotifyTo(String email) {
        return notifyTo.equalsIgnoreCase(email);
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
        mailService.send(to, subject, text);
    }
}

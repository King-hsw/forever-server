package com.forever.server.comment;

import com.forever.server.setting.SiteConfigService;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * 评论邮件通知（CommentCreatedEvent 的订阅者）。
 * 设计原则：通知失败绝不影响评论本身——
 * 未开启开关、未配置 SMTP、发送异常都只记日志。
 */
@Slf4j
@Service
public class CommentNotifyService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final SiteConfigService siteConfig;

    public CommentNotifyService(ObjectProvider<JavaMailSender> mailSenderProvider, SiteConfigService siteConfig) {
        this.mailSenderProvider = mailSenderProvider;
        this.siteConfig = siteConfig;
    }

    /**
     * 监听新评论事件：
     * - 回复他人 -> 通知被回复者
     * - 新的根评论 -> 若配置了 blog.comment.owner-email 则通知站长
     */
    @EventListener
    public void onCommentCreated(CommentCreatedEvent event) {
        Comment comment = event.comment();
        Comment parent = event.parent();
        String articleTitle = event.articleTitle();
        if (!enabled()) {
            return;
        }
        try {
            if (parent != null) {
                send(parent.getEmail(), "你的评论收到了新回复", buildReplyBody(parent, comment, articleTitle));
            } else if (ownerEmail() != null && !ownerEmail().equalsIgnoreCase(comment.getEmail())) {
                send(ownerEmail(), "博客有新的评论",
                        """
                        《%s》收到新评论：

                        昵称：%s
                        内容：%s

                        请登录后台查看与回复。""".formatted(articleTitle, comment.getNickname(), comment.getContent()));
            }
        } catch (Exception e) {
            log.warn("comment notify failed: commentId={}, reason={}", comment.getId(), e.getMessage());
        }
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

    private void send(String to, String subject, String text) throws Exception {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.debug("mail skipped: spring.mail not configured");
            return;
        }
        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(text);
        helper.setFrom(new InternetAddress(siteConfig.getString(SiteConfigService.COMMENT_FROM_EMAIL,
                "noreply@example.com"), "补陋阁"));
        sender.send(message);
        log.info("comment notify mail sent: to={}, subject={}", to, subject);
    }
}

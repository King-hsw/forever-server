package com.forever.server.comment;

import com.forever.server.mail.MailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * 评论通知：仅邮件通道，通知常开（无开关）：
 * 任何评论（根/回复）只要评论者不是站长收件人（blog.mail.notify-to）就通知站长；
 * 回复额外通知被回复者（其本人即站长时跳过，不重复）。
 * 邮件以 HTML（text/html，全 inline style，无外部资源）发送：主题按评论目标（文章/留言板/动态）分支，
 * 正文为 站点头部 → 来源标题 → 评论引用块 → 查看链接 → 页脚；绝对链接取 yml blog.site.url（env BLOG_SITE_URL），未配置时降级为无链接纯文本。
 * 设计原则：通知失败绝不影响评论本身——
 * 未配置 SMTP、发送异常都只记日志。
 */
@Slf4j
@Service
public class CommentNotifyService {

    private final MailService mailService;
    /** 站长收件人（env BLOG_MAIL_NOTIFY_TO 必填） */
    private final String notifyTo;
    /** 站点对外地址（yml blog.site.url / env BLOG_SITE_URL）：邮件绝对链接，空 → 降级无链接 */
    private final String siteUrl;
    /** 站点名（blog.site.name），邮件站点头部 */
    private final String siteName;

    public CommentNotifyService(MailService mailService,
                               @Value("${blog.mail.notify-to}") String notifyTo,
                               @Value("${blog.site.url:}") String siteUrl,
                               @Value("${blog.site.name}") String siteName) {
        this.mailService = mailService;
        this.notifyTo = notifyTo;
        this.siteUrl = siteUrl;
        this.siteName = siteName;
    }

    /**
     * 评论落库后的通知（邮件）：
     * - 根评论/回复 -> 站长（notify-to 收件），评论者即站长时零通知
     * - 回复额外 -> 被回复者（邮件按其邮箱），被回复者即站长时跳过
     * - 邮件主题按评论目标（文章/留言板/动态）分支，正文 HTML
     */
    @EventListener
    public void onCommentCreated(CommentCreatedEvent event) {
        Comment comment = event.comment();
        Comment parent = event.parent();
        String sourceTitle = event.sourceTitle();
        String sourceUrl = event.sourceUrl();
        try {
            if (!isNotifyTo(comment.getEmail())) {
                send(notifyTo, newCommentSubject(comment.getTargetType(), sourceTitle),
                        buildNewCommentHtml(siteName, sourceTitle, sourceUrl, siteUrl,
                                comment.getNickname(), comment.getContent()));
            }
            if (parent != null && !isNotifyTo(parent.getEmail())) {
                send(parent.getEmail(), replySubject(comment.getTargetType()),
                        buildReplyHtml(siteName, sourceTitle, sourceUrl, siteUrl,
                                parent.getNickname(), parent.getContent(),
                                comment.getNickname(), comment.getContent()));
            }
        } catch (Exception e) {
            log.warn("comment notify failed: commentId={}, reason={}", comment.getId(), e.getMessage());
        }
    }

    /**
     * 是否站长收件人（null 安全：notifyTo 必填非空，email 为 null 时返回 false）
     */
    private boolean isNotifyTo(String email) {
        return notifyTo.equalsIgnoreCase(email);
    }

    // ---------- 邮件主题与 HTML 正文纯函数（不依赖 Spring 上下文，单测直接调用） ----------

    /**
     * 邮件主题：新评论（发站长 notify-to）
     */
    static String newCommentSubject(String targetType, String sourceTitle) {
        return switch (targetType) {
            case CommentService.TARGET_BOARD -> "留言板收到新留言";
            case CommentService.TARGET_MOMENT -> "动态收到新评论";
            default -> "《" + sourceTitle + "》收到新评论";
        };
    }

    /**
     * 邮件主题：回复（发被回复者）
     */
    static String replySubject(String targetType) {
        return switch (targetType) {
            case CommentService.TARGET_BOARD -> "你的留言收到了新回复";
            case CommentService.TARGET_MOMENT -> "你的动态评论收到了新回复";
            default -> "你的评论收到了新回复";
        };
    }

    /**
     * 新评论邮件正文（text/html，全 inline style，无外部 CSS/图片/JS）：
     * 站点头部 → 来源标题 → 评论引用块 → 查看链接 → 页脚。
     * siteUrl 为 null/空白时不渲染任何链接，正文仍是完整纯文本描述。
     */
    static String buildNewCommentHtml(String siteName, String sourceTitle, String sourceUrl, String siteUrl,
                                      String nickname, String content) {
        String viewUrl = absoluteUrl(siteUrl, sourceUrl);
        return frame(siteName, sourceTitleHtml(sourceTitle, viewUrl), quoteBlock(nickname, content), viewUrl);
    }

    /**
     * 回复邮件正文：引用块放原评论 + 引用块放新回复，结构同新评论邮件。
     */
    static String buildReplyHtml(String siteName, String sourceTitle, String sourceUrl, String siteUrl,
                                 String parentNickname, String parentContent,
                                 String replyNickname, String replyContent) {
        String viewUrl = absoluteUrl(siteUrl, sourceUrl);
        String body = quoteLabel("原评论") + quoteBlock(parentNickname, parentContent)
                + quoteLabel("新回复") + quoteBlock(replyNickname, replyContent);
        return frame(siteName, sourceTitleHtml(sourceTitle, viewUrl), body, viewUrl);
    }

    private static String frame(String siteName, String sourceTitleHtml, String body, String viewUrl) {
        String viewLink = viewUrl == null ? ""
                : "<div style=\"margin-bottom:12px;\"><a href=\"" + viewUrl
                + "\" style=\"color:#0d9488;\">查看</a></div>";
        return "<div style=\"max-width:600px;margin:0 auto;font-family:Arial,Helvetica,sans-serif;"
                + "font-size:14px;line-height:1.6;color:#333333;\">"
                + "<div style=\"border-bottom:2px solid #0d9488;padding-bottom:12px;margin-bottom:16px;"
                + "font-size:16px;font-weight:bold;\">" + MailService.escapeHtml(siteName) + "</div>"
                + sourceTitleHtml
                + body
                + viewLink
                + "<div style=\"margin-top:24px;font-size:12px;color:#999999;\">此邮件由博客评论通知自动发送，无需回复。</div></div>";
    }

    /**
     * 来源标题：有绝对链接则渲染 <a>，无则纯文本
     */
    private static String sourceTitleHtml(String sourceTitle, String viewUrl) {
        String text = MailService.escapeHtml("《" + sourceTitle + "》");
        String inner = viewUrl == null ? text
                : "<a href=\"" + viewUrl + "\" style=\"color:#0d9488;text-decoration:none;\">" + text + "</a>";
        return "<div style=\"margin-bottom:12px;font-size:15px;font-weight:bold;\">" + inner + "</div>";
    }

    private static String quoteLabel(String label) {
        return "<div style=\"font-weight:bold;margin:12px 0 6px;\">" + MailService.escapeHtml(label) + "</div>";
    }

    /**
     * 评论引用块：左侧品牌色边框 + 浅背景；昵称与内容均 HTML 转义
     */
    private static String quoteBlock(String nickname, String content) {
        return "<div style=\"border-left:3px solid #0d9488;background-color:#f5f5f5;padding:10px 12px;"
                + "margin:0 0 12px;\">"
                + "<div style=\"font-weight:bold;margin-bottom:4px;\">" + MailService.escapeHtml(nickname) + "：</div>"
                + "<div style=\"white-space:pre-wrap;\">" + MailService.escapeHtml(content) + "</div></div>";
    }

    /**
     * 绝对链接 = site.url 去尾部斜杠 + 相对路径；任一无效返回 null（调用方不渲染链接）
     */
    private static String absoluteUrl(String siteUrl, String sourceUrl) {
        if (siteUrl == null || siteUrl.isBlank() || sourceUrl == null || sourceUrl.isBlank()) {
            return null;
        }
        return siteUrl.trim().replaceAll("/+$", "") + sourceUrl;
    }

    private void send(String to, String subject, String html) {
        if (to == null || to.isBlank()) {
            return; // 收件人未留邮箱（登录用户资料无邮箱），不发
        }
        mailService.send(to, subject, html);
    }
}

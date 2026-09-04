package com.forever.server.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;

/**
 * SMTP 发信服务：SMTP 账密由 yml spring.mail（BLOG_MAIL_* 环境变量）提供；
 * 发件人固定为 SMTP 登录账号（BLOG_MAIL_USERNAME，同域发信 163 必过），显示名取站点名（blog.site.name）；
 * 正文以 text/html 发送（helper.setText(html, true)），调用方传 HTML；无标签的纯文本传入会自然降级为纯文本渲染。
 * 发送失败抛 RuntimeException，调用方（评论通知 / 启动测试邮件）捕获记日志、不影响主流程。
 */
@Slf4j
@Service
public class MailService {

    private final JavaMailSenderImpl mailSender;
    private final String siteName;

    public MailService(JavaMailSenderImpl mailSender, @Value("${blog.site.name}") String siteName) {
        this.mailSender = mailSender;
        this.siteName = siteName;
    }

    /**
     * 发送 HTML 邮件（text/html）；失败抛 RuntimeException（调用方捕获记日志，不影响主流程）。
     * <p>html 为完整 HTML 正文，用户输入必须先经 {@link #escapeHtml} 转义再拼入。
     */
    public void send(String to, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(new InternetAddress(mailSender.getUsername(), siteName, "UTF-8"));
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("mail sent: to={}", to);
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new RuntimeException("邮件构建失败：" + e.getMessage(), e);
        } catch (MailException e) {
            Throwable root = e.getCause() != null ? e.getCause() : e;
            throw new RuntimeException("邮件发送失败：" + root.getMessage(), e);
        }
    }

    /**
     * HTML 转义：进 HTML 的用户输入（昵称 / 内容 / 来源标题等）必须先转义。
     * 顺序固定为先 &amp; 再 &lt; &gt; &quot;，避免二次转义（&lt; 变 &amp;lt;）。
     * null 视为空串。
     */
    public static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}

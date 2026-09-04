package com.forever.server.mail;

import com.forever.server.setting.SiteConfigService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;

/**
 * SMTP 发信服务：SMTP 账密由 yml spring.mail（BLOG_MAIL_* 环境变量）提供；
 * 发件人固定为 SMTP 登录账号（BLOG_MAIL_USERNAME，同域发信 163 必过），显示名取站点名；
 * 发送失败抛 RuntimeException，调用方（评论通知 / 启动测试邮件）捕获记日志、不影响主流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSenderImpl mailSender;
    private final SiteConfigService siteConfig;

    /**
     * 发送邮件；失败抛 RuntimeException（调用方捕获记日志，不影响主流程）
     */
    public void send(String to, String subject, String text) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(new InternetAddress(mailSender.getUsername(), siteConfig.siteName(), "UTF-8"));
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text);
            mailSender.send(message);
            log.info("mail sent: to={}", to);
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new RuntimeException("邮件构建失败：" + e.getMessage(), e);
        } catch (MailException e) {
            Throwable root = e.getCause() != null ? e.getCause() : e;
            throw new RuntimeException("邮件发送失败：" + root.getMessage(), e);
        }
    }
}

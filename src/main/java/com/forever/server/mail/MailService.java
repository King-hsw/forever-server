package com.forever.server.mail;

import com.forever.server.common.BizException;
import com.forever.server.common.ErrorCode;
import com.forever.server.setting.SiteConfigService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;

/**
 * SMTP 发信服务：SMTP 账密由 yml spring.mail（BLOG_MAIL_* 环境变量）提供、
 * Spring Boot 自动装配 JavaMailSender；发件人地址取后台配置 comment.from-email。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;
    private final SiteConfigService siteConfig;

    /**
     * 发送邮件；发送失败抛 BizException（调用方捕获记日志，不影响主流程）
     */
    public void send(String to, String subject, String text) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text);
            helper.setFrom(new InternetAddress(
                    siteConfig.getString(SiteConfigService.COMMENT_FROM_EMAIL, "noreply@example.com"),
                    siteConfig.siteName()));
            mailSender.send(message);
            log.info("mail sent: to={}", to);
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new BizException(ErrorCode.BAD_REQUEST, "构建邮件失败：" + e.getMessage());
        } catch (MailException e) {
            Throwable root = e.getCause() != null ? e.getCause() : e;
            throw new BizException(ErrorCode.BAD_REQUEST, "邮件发送失败：" + root.getMessage());
        }
    }
}

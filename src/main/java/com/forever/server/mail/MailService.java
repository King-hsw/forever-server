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
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.util.Properties;

/**
 * SMTP 发信服务：账密取自后台配置（mail.*），发送时按需构建 sender。
 * <p>
 * 低频场景（每天几条评论通知），不缓存构建实例——
 * 账号在后台随时改、即时生效，无需重启。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final SiteConfigService siteConfig;

    /**
     * SMTP 是否已配置（服务器地址非空）
     */
    public boolean configured() {
        return siteConfig.getString(SiteConfigService.MAIL_HOST, null) != null;
    }

    /**
     * 发送邮件；SMTP 未配置或发送失败抛 BizException
     * （测试邮件接口直接透出错误；评论通知方在外层捕获记日志）。
     */
    public void send(String to, String subject, String text) {
        if (!configured()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "SMTP 未配置：请先填写 mail.host");
        }
        try {
            JavaMailSenderImpl sender = buildSender();
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text);
            helper.setFrom(new InternetAddress(
                    siteConfig.getString(SiteConfigService.COMMENT_FROM_EMAIL, "noreply@example.com"),
                    siteConfig.siteName()));
            sender.send(message);
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new BizException(ErrorCode.BAD_REQUEST, "构建邮件失败：" + e.getMessage());
        } catch (MailException e) {
            Throwable root = e.getCause() != null ? e.getCause() : e;
            throw new BizException(ErrorCode.BAD_REQUEST, "邮件发送失败：" + root.getMessage());
        }
        log.info("mail sent: to={}", to);
    }

    private JavaMailSenderImpl buildSender() {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(siteConfig.getString(SiteConfigService.MAIL_HOST, null));
        sender.setPort((int) siteConfig.getLong(SiteConfigService.MAIL_PORT, 465));
        sender.setUsername(siteConfig.getString(SiteConfigService.MAIL_USERNAME, null));
        sender.setPassword(siteConfig.getString(SiteConfigService.MAIL_PASSWORD, null));
        Properties props = new Properties();
        if (siteConfig.getBoolean(SiteConfigService.MAIL_SSL, true)) {
            props.put("mail.smtp.ssl.enable", "true");
        } else {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        }
        sender.setJavaMailProperties(props);
        return sender;
    }
}

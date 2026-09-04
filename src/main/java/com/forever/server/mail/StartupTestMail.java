package com.forever.server.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 服务启动时向 yml 配置的收件人（blog.mail.test-to / BLOG_MAIL_TO）发一封测试邮件，
 * 验证 SMTP 配置可用；失败只记日志，不阻塞启动。
 */
@Slf4j
@Component
public class StartupTestMail implements ApplicationRunner {

    private final MailService mailService;
    private final String to;

    public StartupTestMail(MailService mailService, @Value("${blog.mail.test-to}") String to) {
        this.mailService = mailService;
        this.to = to;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            mailService.send(to, "博客服务启动测试邮件",
                    "这是服务启动时自动发送的测试邮件，收到即说明 SMTP 配置可用。");
            log.info("startup test mail sent: to={}", to);
        } catch (Exception e) {
            log.error("startup test mail failed: {}", e.getMessage());
        }
    }
}

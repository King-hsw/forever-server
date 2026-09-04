package com.forever.server.mail;

import com.forever.server.setting.SiteConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 服务启动时向站长邮箱（comment.owner-email）发一封测试邮件，
 * 验证 yml 里的 SMTP 配置可用；失败只记日志，不阻塞启动。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StartupTestMail implements ApplicationRunner {

    private final MailService mailService;
    private final SiteConfigService siteConfig;

    @Override
    public void run(ApplicationArguments args) {
        String to = siteConfig.getString(SiteConfigService.COMMENT_OWNER_EMAIL, null);
        if (to == null || to.isBlank()) {
            log.warn("startup test mail skipped: comment.owner-email 未设置");
            return;
        }
        try {
            mailService.send(to, "博客服务启动测试邮件",
                    "这是服务启动时自动发送的测试邮件，收到即说明 SMTP 配置可用。");
            log.info("startup test mail sent: to={}", to);
        } catch (Exception e) {
            log.error("startup test mail failed: {}", e.getMessage());
        }
    }
}

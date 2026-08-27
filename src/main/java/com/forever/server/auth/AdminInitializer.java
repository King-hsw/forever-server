package com.forever.server.auth;

import com.forever.server.config.BlogProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 单管理员初始化：应用启动时检测 sys_user 为空，
 * 则按 blog.admin.* 配置创建管理员（密码 BCrypt 存储）。
 * 生产环境务必通过环境变量覆盖默认账密。
 */
@Component
public class AdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final BlogProperties props;

    public AdminInitializer(SysUserMapper sysUserMapper,
                            PasswordEncoder passwordEncoder,
                            BlogProperties props) {
        this.sysUserMapper = sysUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (sysUserMapper.countAll() > 0) {
            return;
        }
        SysUser admin = new SysUser();
        admin.setUsername(props.admin().username());
        admin.setPassword(passwordEncoder.encode(props.admin().password()));
        admin.setNickname("admin");
        admin.setStatus("ACTIVE");
        sysUserMapper.insert(admin);
        log.info("initialized admin user '{}', please change the default credentials",
                admin.getUsername());
    }
}

package com.forever.server.auth;

import com.forever.server.actionlog.ActionLogService;
import com.forever.server.common.BizException;
import com.forever.server.common.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AuthService {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ActionLogService actionLogService;
    /** 登录接口路径，审计日志用 */
    private static final String LOGIN_PATH = "/api/auth/login";

    public AuthService(SysUserMapper sysUserMapper,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       ActionLogService actionLogService) {
        this.sysUserMapper = sysUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.actionLogService = actionLogService;
    }

    public LoginResponse login(LoginRequest request, String ip) {
        SysUser user = sysUserMapper.findByUsername(request.username());
        // 用户不存在或密码错误统一返回 40101，不区分原因（避免账号枚举）
        if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            log.warn("login failed: username={}, ip={}", request.username(), ip);
            // 登录失败显式入审计日志（含尝试的用户名），拦截器拿不到这个信息
            actionLogService.record(request.username(), "POST", LOGIN_PATH, 401, ip, null);
            throw new BizException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (!RbacService.STATUS_ACTIVE.equals(user.getStatus())) {
            actionLogService.record(request.username(), "POST", LOGIN_PATH, 403, ip, null);
            throw new BizException(ErrorCode.FORBIDDEN, "账号已被禁用");
        }
        String accessToken = jwtService.issue(user.getId(), user.getUsername());
        actionLogService.record(user.getUsername(), "POST", LOGIN_PATH, HttpStatus.OK.value(), ip, null);
        log.info("login success: uid={}, username={}", user.getId(), user.getUsername());
        return new LoginResponse(accessToken, jwtService.expiresInSeconds());
    }
}

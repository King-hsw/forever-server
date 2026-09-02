package com.forever.server.auth;

import com.forever.server.actionlog.ActionLogService;
import com.forever.server.common.BizException;
import com.forever.server.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 登录认证：用户名密码校验 + 签发双 Token（见 {@link TokenService}）。
 * 登录成败均写入审计日志（action_log），供后台「审计日志」页追溯。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final ActionLogService actionLogService;
    /**
     * 登录接口路径，审计日志用
     */
    private static final String LOGIN_PATH = "/api/auth/login";

    public LoginResponse login(LoginRequest request, String ip) {
        SysUser user = sysUserMapper.findByUsername(request.username());
        // 用户不存在或密码错误统一返回 40101，不区分原因（避免账号枚举）
        if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            log.warn("login failed: username={}, ip={}", request.username(), ip);
            // 登录失败显式入审计日志（含尝试的用户名），拦截器拿不到这个信息
            actionLogService.record(request.username(), "POST", LOGIN_PATH, 401, ip, null);
            throw new BizException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            log.warn("login rejected: account disabled, username={}, ip={}", request.username(), ip);
            actionLogService.record(request.username(), "POST", LOGIN_PATH, 403, ip, null);
            throw new BizException(ErrorCode.FORBIDDEN, "账号已被禁用");
        }
        LoginResponse tokens = tokenService.issue(user.getId());
        actionLogService.record(user.getUsername(), "POST", LOGIN_PATH, HttpStatus.OK.value(), ip, null);
        log.info("login success: uid={}, username={}", user.getId(), user.getUsername());
        return tokens;
    }
}

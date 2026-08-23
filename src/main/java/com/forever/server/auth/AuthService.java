package com.forever.server.auth;

import com.forever.server.common.BizException;
import com.forever.server.common.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AuthService {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(SysUserMapper sysUserMapper,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.sysUserMapper = sysUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {
        SysUser user = sysUserMapper.findByUsername(request.username());
        // 用户不存在或密码错误统一返回 40101，不区分原因（避免账号枚举）
        if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            log.warn("login failed: username={}", request.username());
            throw new BizException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        String accessToken = jwtService.issue(user.getId(), user.getUsername());
        log.info("login success: uid={}, username={}", user.getId(), user.getUsername());
        return new LoginResponse(accessToken, jwtService.expiresInSeconds());
    }
}

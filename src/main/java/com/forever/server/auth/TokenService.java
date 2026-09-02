package com.forever.server.auth;

import com.forever.server.common.BizException;
import com.forever.server.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * 双 Token 登录态：access（短效，随请求提交）+ refresh（长效，仅用于换新）。
 * 均为随机不透明串，库里只存 SHA-256；任何一端失效/删除即登出，
 * 删号、禁用、清库后旧 token 立刻不可用（区别于自包含 JWT）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    public static final long ACCESS_TTL_SECONDS = 2 * 3600;          // 2 小时
    public static final long REFRESH_TTL_DAYS = 30;                  // 30 天

    private final SysAuthTokenMapper mapper;
    private final SysUserMapper userMapper;
    private final SecureRandom random = new SecureRandom();

    /**
     * 登录：签发新令牌对
     */
    public LoginResponse issue(long uid) {
        mapper.purgeExpired();
        return createPair(uid);
    }

    /**
     * 换发：校验 refresh 有效 → 旧行作废，签发新令牌对（轮换防重放）
     */
    public LoginResponse rotate(String rawRefreshToken) {
        SysAuthToken row = mapper.findByRefreshToken(sha256(rawRefreshToken));
        if (row == null || expired(row.getRefreshExpiresAt())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "登录已过期，请重新登录");
        }
        requireActiveUser(row.getUserId());
        mapper.deleteById(row.getId());
        log.info("token rotated: uid={}", row.getUserId());
        return createPair(row.getUserId());
    }

    /**
     * 校验 access token，返回用户身份；无效返回 null（按未认证处理）
     */
    public AuthPrincipal resolve(String rawAccessToken) {
        if (rawAccessToken == null || rawAccessToken.isBlank()) {
            return null;
        }
        SysAuthToken row = mapper.findByAccessToken(sha256(rawAccessToken));
        if (row == null || expired(row.getAccessExpiresAt())) {
            return null;
        }
        SysUser user = requireActiveUser(row.getUserId());
        return new AuthPrincipal(user.getId(), user.getUsername());
    }

    /**
     * 登出：吊销该 refresh 对应的整个会话
     */
    public void revokeByRefreshToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        SysAuthToken row = mapper.findByRefreshToken(sha256(rawRefreshToken));
        mapper.deleteByRefreshToken(sha256(rawRefreshToken));
        if (row != null) {
            log.info("token revoked (logout): uid={}", row.getUserId());
        }
    }

    // ---------- internal ----------

    private LoginResponse createPair(long uid) {
        String access = randomToken();
        String refresh = randomToken();
        LocalDateTime now = LocalDateTime.now();
        SysAuthToken row = new SysAuthToken();
        row.setUserId(uid);
        row.setAccessToken(sha256(access));
        row.setRefreshToken(sha256(refresh));
        row.setAccessExpiresAt(now.plusSeconds(ACCESS_TTL_SECONDS));
        row.setRefreshExpiresAt(now.plusDays(REFRESH_TTL_DAYS));
        mapper.insert(row);
        log.info("token pair issued: uid={}", uid);
        return new LoginResponse(access, refresh, ACCESS_TTL_SECONDS, REFRESH_TTL_DAYS * 24 * 3600);
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes); // 64 位十六进制明文，只出现一次
    }

    private static String sha256(String raw) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean expired(LocalDateTime expiresAt) {
        return expiresAt == null || expiresAt.isBefore(LocalDateTime.now());
    }

    private SysUser requireActiveUser(long uid) {
        SysUser user = userMapper.findById(uid);
        if (user == null) {
            throw new BadCredentialsException("用户不存在");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BadCredentialsException("账号已被禁用");
        }
        return user;
    }
}

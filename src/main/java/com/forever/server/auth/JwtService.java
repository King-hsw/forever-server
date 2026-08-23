package com.forever.server.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.forever.server.config.BlogProperties;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * 登录时签发 HS256 JWT；解析与校验交给 resource server 的 JwtDecoder（见 SecurityConfig）。
 */
@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long expireHours;

    public JwtService(SecretKey secretKey, BlogProperties props) {
        this.secretKey = secretKey;
        this.expireHours = props.jwt().expireHours();
    }

    /** 有效期（秒），随登录响应返回给前端 */
    public long expiresInSeconds() {
        return expireHours * 3600;
    }

    public String issue(long uid, String username) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(username)
                .claim("uid", uid)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(expireHours, ChronoUnit.HOURS)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            jwt.sign(new MACSigner(secretKey));
        } catch (JOSEException e) {
            throw new IllegalStateException("JWT 签发失败", e);
        }
        return jwt.serialize();
    }
}

package com.forever.server.auth;

import java.time.LocalDateTime;

/** 已认证用户身份（作为 SecurityContext 的 principal） */
public record AuthPrincipal(long uid, String username) {
}

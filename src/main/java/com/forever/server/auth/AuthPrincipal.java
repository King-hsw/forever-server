package com.forever.server.auth;

/** 已认证用户身份（作为 SecurityContext 的 principal） */
public record AuthPrincipal(long uid, String username) {
}

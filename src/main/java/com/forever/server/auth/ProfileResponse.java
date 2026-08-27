package com.forever.server.auth;

/**
 * 当前登录用户资料（对应前端 ProfileInfo）；avatarUrl 已解析为可直接展示的地址。
 */
public record ProfileResponse(
        String username,
        String nickname,
        String email,
        String site,
        /** 自定义头像 URL，或按邮箱 hash 生成的 Gravatar 地址；均无时为 null */
        String avatarUrl) {
}

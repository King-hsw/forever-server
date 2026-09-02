package com.forever.server.auth;

import com.forever.server.common.BizException;
import com.forever.server.common.ErrorCode;
import com.forever.server.common.Strings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 当前登录用户的个人资料：昵称 / 邮箱 / 主页、头像（Gravatar 兜底）、修改密码。
 * 自定义头像上传已下线：avatar_url 仅保留历史数据的直链渲染。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileService {

    static final long MAX_AVATAR_BYTES = 2L * 1024 * 1024;

    private static final Map<String, String> EXT_BY_CONTENT_TYPE = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp");

    private static final String EMAIL_RE = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;

    public ProfileResponse profileOf(long uid) {
        return toResponse(requireUser(uid));
    }

    public ProfileResponse update(long uid, String nickname, String email, String site) {
        requireUser(uid);
        String nicknameNorm = Strings.blankToNull(nickname);
        if (nicknameNorm != null && nicknameNorm.length() > 50) {
            throw new BizException(ErrorCode.BAD_REQUEST, "昵称最长 50 字");
        }
        String emailNorm = Strings.blankToNull(email);
        if (emailNorm != null && !emailNorm.matches(EMAIL_RE)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "邮箱格式不正确");
        }
        String siteNorm = Strings.blankToNull(site);
        if (siteNorm != null) {
            siteNorm = Strings.checkHttpUrl(siteNorm, "个人主页");
        }
        sysUserMapper.updateProfile(uid, nicknameNorm, emailNorm, siteNorm);
        log.debug("profile updated: uid={}", uid);
        return profileOf(uid);
    }

    public void changePassword(long uid, String oldPassword, String newPassword) {
        SysUser user = requireUser(uid);
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            log.warn("password change rejected: wrong old password, uid={}", uid);
            throw new BizException(ErrorCode.BAD_REQUEST, "原密码不正确");
        }
        sysUserMapper.updatePassword(uid, passwordEncoder.encode(newPassword));
        log.info("password changed: uid={}", uid);
    }

    private SysUser requireUser(long uid) {
        SysUser user = sysUserMapper.findById(uid);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    /**
     * 头像展示地址：历史自定义直链优先，否则按邮箱 hash 取 Gravatar
     */
    static ProfileResponse toResponse(SysUser user) {
        String avatar = user.getAvatarUrl();
        if (avatar == null && user.getEmail() != null) {
            avatar = Strings.gravatarUrl(user.getEmail());
        }
        return new ProfileResponse(user.getUsername(), user.getNickname(),
                user.getEmail(), user.getSite(), avatar);
    }
}

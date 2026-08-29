package com.forever.server.auth;

import com.forever.server.common.BizException;
import com.forever.server.common.ErrorCode;
import com.forever.server.common.Strings;
import com.forever.server.storage.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * 当前登录用户的个人资料：昵称 / 邮箱 / 主页、头像（本地上传 + Gravatar 兜底）、修改密码。
 * 头像经 {@link StorageService} 落到配置的存储后端，地址恒为 /uploads/avatar/**。
 */
@Slf4j
@Service
public class ProfileService {

    static final long MAX_AVATAR_BYTES = 2L * 1024 * 1024;

    private static final Map<String, String> EXT_BY_CONTENT_TYPE = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp");

    private static final String EMAIL_RE = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final StorageService storage;

    public ProfileService(SysUserMapper sysUserMapper,
                          PasswordEncoder passwordEncoder,
                          StorageService storage) {
        this.sysUserMapper = sysUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.storage = storage;
    }

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

    /** 保存头像到存储后端（avatar/avatar-{uid}.{ext}），返回携带新头像 URL 的资料 */
    public ProfileResponse uploadAvatar(long uid, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "未选择文件");
        }
        String ext = EXT_BY_CONTENT_TYPE.get(file.getContentType());
        if (ext == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "仅支持 jpg / png / webp 图片");
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            throw new BizException(ErrorCode.BAD_REQUEST, "头像不能超过 2MB");
        }
        SysUser user = requireUser(uid);
        // 换扩展名时删掉旧文件，避免存储里堆积死图
        deleteUrlFile(user.getAvatarUrl());
        String url;
        try {
            url = storage.save("avatar/avatar-" + uid + ext,
                    file.getContentType(), file.getInputStream(), file.getSize());
        } catch (IOException e) {
            log.error("保存头像失败: uid={}", uid, e);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "头像保存失败");
        }
        sysUserMapper.updateAvatarUrl(uid, url);
        log.info("avatar uploaded: uid={}, size={}B", uid, file.getSize());
        return profileOf(uid);
    }

    /** 删除自定义头像，回落为邮箱 Gravatar */
    public ProfileResponse removeAvatar(long uid) {
        SysUser user = requireUser(uid);
        deleteUrlFile(user.getAvatarUrl());
        sysUserMapper.updateAvatarUrl(uid, null);
        log.info("avatar removed: uid={}", uid);
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

    private void deleteUrlFile(String avatarUrl) {
        String relative = StorageService.relativeOf(avatarUrl);
        if (relative == null || !relative.startsWith("avatar/")) {
            return;
        }
        storage.delete(relative);
    }

    /** 头像展示地址：自定义上传优先，其次按邮箱 hash 取 Gravatar，均无则 null */
    static ProfileResponse toResponse(SysUser user) {
        String avatar = user.getAvatarUrl();
        if (avatar == null && user.getEmail() != null) {
            avatar = Strings.gravatarUrl(user.getEmail());
        }
        return new ProfileResponse(user.getUsername(), user.getNickname(),
                user.getEmail(), user.getSite(), avatar);
    }
}

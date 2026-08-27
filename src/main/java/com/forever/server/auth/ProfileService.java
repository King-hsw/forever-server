package com.forever.server.auth;

import com.forever.server.common.BizException;
import com.forever.server.common.ErrorCode;
import com.forever.server.common.Strings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/**
 * 当前登录用户的个人资料：昵称 / 邮箱 / 主页、头像（本地上传 + Gravatar 兜底）、修改密码。
 * 头像落盘 data/uploads/avatar/，经 WebConfig 的 /uploads/** 静态映射对外提供。
 */
@Slf4j
@Service
public class ProfileService {

    /** 与 WebConfig 的 /uploads/** 静态映射对应（相对工作目录） */
    public static final Path UPLOAD_ROOT = Paths.get("data", "uploads");

    static final long MAX_AVATAR_BYTES = 2L * 1024 * 1024;
    private static final String UPLOAD_URL_PREFIX = "/uploads/avatar/";

    private static final Map<String, String> EXT_BY_CONTENT_TYPE = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp");

    private static final String EMAIL_RE = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;

    public ProfileService(SysUserMapper sysUserMapper, PasswordEncoder passwordEncoder) {
        this.sysUserMapper = sysUserMapper;
        this.passwordEncoder = passwordEncoder;
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
        return profileOf(uid);
    }

    /** 保存头像到 data/uploads/avatar/，返回携带新头像 URL 的资料 */
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
        String url = UPLOAD_URL_PREFIX + "avatar-" + uid + ext;
        Path target = Paths.get("data", "uploads", "avatar", "avatar-" + uid + ext);
        // 换扩展名时删掉旧文件，避免 data 目录里堆积死图
        deleteUrlFile(user.getAvatarUrl());
        try {
            Files.createDirectories(target.getParent());
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("保存头像失败: uid={}", uid, e);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "头像保存失败");
        }
        sysUserMapper.updateAvatarUrl(uid, url);
        return profileOf(uid);
    }

    /** 删除自定义头像，回落为邮箱 Gravatar */
    public ProfileResponse removeAvatar(long uid) {
        SysUser user = requireUser(uid);
        deleteUrlFile(user.getAvatarUrl());
        sysUserMapper.updateAvatarUrl(uid, null);
        return profileOf(uid);
    }

    public void changePassword(long uid, String oldPassword, String newPassword) {
        SysUser user = requireUser(uid);
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "原密码不正确");
        }
        sysUserMapper.updatePassword(uid, passwordEncoder.encode(newPassword));
    }

    private SysUser requireUser(long uid) {
        SysUser user = sysUserMapper.findById(uid);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    private static void deleteUrlFile(String avatarUrl) {
        if (avatarUrl == null || !avatarUrl.startsWith(UPLOAD_URL_PREFIX)) {
            return;
        }
        try {
            Files.deleteIfExists(UPLOAD_ROOT.resolve("avatar")
                    .resolve(avatarUrl.substring(UPLOAD_URL_PREFIX.length())));
        } catch (IOException e) {
            log.warn("删除旧头像文件失败: {}", avatarUrl);
        }
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

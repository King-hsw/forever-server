package com.forever.server.auth;

import com.forever.server.common.BizException;
import com.forever.server.common.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * RBAC 核心：权限缓存 + 用户/角色管理。
 * <p>
 * 权限集缓存在内存（uid -> 权限码），角色权限调配、用户禁用/换角后调用 {@link #evict}
 * 即时失效；用户量大或多实例部署时再考虑集中缓存。
 */
@Slf4j
@Service
public class RbacService {

    public static final String PERM_ADMIN_ACCESS = "admin:access";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";

    private final RbacMapper rbacMapper;
    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    /** uid -> 权限码集合；DISABLED 用户为空集合 */
    private final Map<Long, Set<String>> permsByUser = new ConcurrentHashMap<>();

    public RbacService(RbacMapper rbacMapper, SysUserMapper sysUserMapper, PasswordEncoder passwordEncoder) {
        this.rbacMapper = rbacMapper;
        this.sysUserMapper = sysUserMapper;
        this.passwordEncoder = passwordEncoder;
    }

    // ---------- 鉴权 ----------

    /** 用户的全部权限码；查库后缓存。DISABLED 用户返回空集，立即失去所有访问。 */
    public Set<String> permissionsOf(long uid) {
        return permsByUser.computeIfAbsent(uid, id -> {
            Set<String> codes = rbacMapper.permissionsByUserId(id).stream()
                    .map(SysPermission::getCode)
                    .collect(Collectors.toSet());
            log.debug("loaded {} permissions for uid={}", codes.size(), id);
            return codes;
        });
    }

    /** 用户的角色列表（管理端展示 / me 接口用） */
    public List<SysRole> rolesOf(long uid) {
        Map<Long, SysRole> byId = rbacMapper.listRoles().stream()
                .collect(Collectors.toMap(SysRole::getId, r -> r));
        return rbacMapper.roleIdsByUserId(uid).stream()
                .map(byId::get).filter(r -> r != null).toList();
    }

    public boolean has(long uid, String permissionCode) {
        return permissionsOf(uid).contains(permissionCode);
    }

    /** 角色/用户关系变更后失效对应缓存；uid 传 null 清空全部 */
    public void evict(Long uid) {
        if (uid == null) {
            permsByUser.clear();
        } else {
            permsByUser.remove(uid);
        }
    }

    // ---------- 用户管理 ----------

    public List<SysUser> listUsers() {
        List<SysRole> allRoles = rbacMapper.listRoles();
        Map<Long, SysRole> roleById = allRoles.stream()
                .collect(Collectors.toMap(SysRole::getId, r -> r));
        return sysUserMapper.listAll().stream().peek(u ->
                u.setRoles(rbacMapper.roleIdsByUserId(u.getId()).stream()
                        .map(roleById::get).filter(r -> r != null).toList())
        ).toList();
    }

    /** 用户当前角色编码集合 */
    public Set<String> roleCodesOf(long uid) {
        return rolesOf(uid).stream().map(SysRole::getCode).collect(Collectors.toSet());
    }

    public SysUser createUser(String username, String rawPassword, String nickname, List<Long> roleIds) {
        if (sysUserMapper.findByUsername(username) != null) {
            throw new BizException(ErrorCode.CONFLICT, "用户名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setNickname(nickname == null || nickname.isBlank() ? username : nickname.trim());
        user.setStatus(STATUS_ACTIVE);
        sysUserMapper.insert(user);
        assignRoles(user.getId(), roleIds);
        log.info("user created: id={}, username={}, roles={}", user.getId(), username, roleIds);
        return user;
    }

    public void updateStatus(Long uid, String status) {
        requireUser(uid);
        if (!STATUS_ACTIVE.equals(status) && !STATUS_DISABLED.equals(status)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "status 只接受 ACTIVE / DISABLED");
        }
        sysUserMapper.updateStatus(uid, status);
        evict(uid);
        log.info("user {}: uid={}", status.toLowerCase(), uid);
    }

    public void resetPassword(Long uid, String rawPassword) {
        requireUser(uid);
        sysUserMapper.updatePassword(uid, passwordEncoder.encode(rawPassword));
    }

    public void assignRoles(Long uid, List<Long> roleIds) {
        requireUser(uid);
        rbacMapper.deleteUserRoles(uid);
        if (roleIds != null) {
            roleIds.forEach(roleId -> rbacMapper.insertUserRole(uid, requireRole(roleId).getId()));
        }
        evict(uid);
    }

    // ---------- 角色管理 ----------

    public List<Map<String, Object>> listRolesWithPermissions() {
        return rbacMapper.listRoles().stream().map(r -> Map.<String, Object>of(
                "id", r.getId(),
                "code", r.getCode(),
                "name", r.getName(),
                "remark", r.getRemark() == null ? "" : r.getRemark(),
                "builtIn", r.isBuiltIn(),
                "permissionIds", rbacMapper.permissionIdsByRoleId(r.getId())
        )).toList();
    }

    /** 覆盖式设置角色权限 */
    public void updateRolePermissions(Long roleId, List<Long> permissionIds) {
        requireRole(roleId);
        rbacMapper.deleteRolePermissions(roleId);
        if (permissionIds != null) {
            permissionIds.forEach(pid -> rbacMapper.insertRolePermission(roleId, pid));
        }
        evict(null); // 该角色的所有用户权限都变了
        log.info("role permissions updated: roleId={}, count={}", roleId,
                permissionIds == null ? 0 : permissionIds.size());
    }

    public SysRole createRole(String code, String name, String remark) {
        if (!code.matches("[A-Z_]{2,30}")) {
            throw new BizException(ErrorCode.BAD_REQUEST, "角色编码须为大写字母/下划线，2-30 位");
        }
        if (rbacMapper.findRoleByCode(code) != null) {
            throw new BizException(ErrorCode.CONFLICT, "角色编码已存在");
        }
        SysRole role = new SysRole();
        role.setCode(code);
        role.setName(name);
        role.setRemark(remark);
        rbacMapper.insertRole(role);
        return role;
    }

    public void deleteRole(Long roleId) {
        SysRole role = requireRole(roleId);
        if (role.isBuiltIn()) {
            throw new BizException(ErrorCode.CONFLICT, "内置角色不可删除");
        }
        if (rbacMapper.countUsersWithRole(role.getCode()) > 0) {
            throw new BizException(ErrorCode.CONFLICT, "仍有用户持有该角色，请先解除");
        }
        rbacMapper.deleteRole(roleId);
        evict(null);
    }

    public List<SysPermission> listPermissions() {
        return rbacMapper.listPermissions();
    }

    // ---------- internal ----------

    private SysUser requireUser(Long uid) {
        SysUser user = sysUserMapper.findById(uid);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    private SysRole requireRole(Long roleId) {
        SysRole role = rbacMapper.findRoleById(roleId);
        if (role == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "角色不存在");
        }
        return role;
    }
}

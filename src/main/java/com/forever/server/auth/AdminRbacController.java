package com.forever.server.auth;

import com.forever.server.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 用户与角色管理。各端点由 rbac:* 端点级权限码保护。
 */
@Tag(name = "用户与角色管理", description = "后台开号、启停用、分配角色；角色权限矩阵调配")
@RestController
@RequestMapping("/api/admin")
public class AdminRbacController {

    private final RbacService rbacService;

    public AdminRbacController(RbacService rbacService) {
        this.rbacService = rbacService;
    }

    // ---------- DTO ----------

    public record UserCreateRequest(
            @NotBlank(message = "用户名不能为空") @Size(max = 50) String username,
            @NotBlank(message = "密码不能为空") @Size(min = 6, max = 100, message = "密码 6-100 位") String password,
            @Size(max = 50) String nickname,
            List<Long> roleIds) {
    }

    public record StatusRequest(String status) {
    }

    public record PasswordResetRequest(
            @NotBlank(message = "密码不能为空") @Size(min = 6, max = 100, message = "密码 6-100 位") String password) {
    }

    public record RolesRequest(List<Long> roleIds) {
    }

    public record RoleCreateRequest(
            @NotBlank String code,
            @NotBlank String name,
            String remark) {
    }

    public record RolePermissionsRequest(List<Long> permissionIds) {
    }

    /** 用户视图：不回传密码 */
    public record UserView(Long id, String username, String nickname, String status,
                           List<SysRole> roles, java.time.LocalDateTime createdAt) {
    }

    // ---------- 用户 ----------

    @Perm("rbac:user:list")
    @Operation(summary = "用户列表", description = "全量，含角色")
    @GetMapping("/users")
    public ApiResponse<List<UserView>> listUsers() {
        return ApiResponse.ok(rbacService.listUsers().stream()
                .map(u -> new UserView(u.getId(), u.getUsername(), u.getNickname(),
                        u.getStatus(), u.getRoles(), u.getCreatedAt()))
                .toList());
    }

    @Perm("rbac:user:create")
    @Operation(summary = "创建用户", description = "后台开号，指定初始角色（如 MEMBER/USER）")
    @PostMapping("/users")
    public ApiResponse<UserView> createUser(@Valid @RequestBody UserCreateRequest request) {
        SysUser created = rbacService.createUser(request.username(), request.password(),
                request.nickname(), request.roleIds());
        return ApiResponse.ok(new UserView(created.getId(), created.getUsername(),
                created.getNickname(), created.getStatus(), List.of(), created.getCreatedAt()));
    }

    @Perm("rbac:user:status")
    @Operation(summary = "启用/禁用用户", description = "DISABLED 立即失去全部权限且无法登录")
    @PutMapping("/users/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @RequestBody StatusRequest request) {
        rbacService.updateStatus(id, request.status());
        return ApiResponse.ok();
    }

    @Perm("rbac:user:password")
    @Operation(summary = "重置密码")
    @PutMapping("/users/{id}/password")
    public ApiResponse<Void> resetPassword(@PathVariable Long id,
                                           @Valid @RequestBody PasswordResetRequest request) {
        rbacService.resetPassword(id, request.password());
        return ApiResponse.ok();
    }

    @Perm("rbac:user:roles")
    @Operation(summary = "分配角色", description = "覆盖式设置用户的角色列表")
    @PutMapping("/users/{id}/roles")
    public ApiResponse<Void> assignRoles(@PathVariable Long id, @RequestBody RolesRequest request) {
        rbacService.assignRoles(id, request.roleIds());
        return ApiResponse.ok();
    }

    // ---------- 角色与权限 ----------

    @Perm("rbac:role:list")
    @Operation(summary = "角色列表", description = "含每个角色的权限 id 集合")
    @GetMapping("/roles")
    public ApiResponse<List<Map<String, Object>>> listRoles() {
        return ApiResponse.ok(rbacService.listRolesWithPermissions());
    }

    @Perm("rbac:role:create")
    @Operation(summary = "新建角色")
    @PostMapping("/roles")
    public ApiResponse<SysRole> createRole(@Valid @RequestBody RoleCreateRequest request) {
        return ApiResponse.ok(rbacService.createRole(request.code(), request.name(), request.remark()));
    }

    @Perm("rbac:role:delete")
    @Operation(summary = "删除角色", description = "仅可删非内置且无人持有的角色")
    @DeleteMapping("/roles/{id}")
    public ApiResponse<Void> deleteRole(@PathVariable Long id) {
        rbacService.deleteRole(id);
        return ApiResponse.ok();
    }

    @Perm("rbac:role:permissions")
    @Operation(summary = "调配角色权限", description = "覆盖式设置该角色的权限集合，保存后即时生效")
    @PutMapping("/roles/{id}/permissions")
    public ApiResponse<Void> updateRolePermissions(@PathVariable Long id,
                                                   @RequestBody RolePermissionsRequest request) {
        rbacService.updateRolePermissions(id, request.permissionIds());
        return ApiResponse.ok();
    }

    @Perm("rbac:permission:list")
    @Operation(summary = "权限点列表", description = "全部可选权限，按模块分组展示")
    @GetMapping("/permissions")
    public ApiResponse<List<SysPermission>> listPermissions() {
        return ApiResponse.ok(rbacService.listPermissions());
    }
}

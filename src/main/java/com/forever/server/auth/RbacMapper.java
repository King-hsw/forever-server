package com.forever.server.auth;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RbacMapper {

    List<SysPermission> permissionsByUserId(Long uid);

    List<Long> roleIdsByUserId(Long uid);

    List<SysRole> listRoles();

    SysRole findRoleByCode(String code);

    SysRole findRoleById(Long id);

    List<Long> permissionIdsByRoleId(Long roleId);

    List<SysPermission> listPermissions();

    /** 幂等插入权限点；已存在返回 0，新插入返回 1 */
    int insertPermissionIfAbsent(@Param("code") String code, @Param("name") String name,
                                 @Param("module") String module);

    /** 按权限码授予内置 ADMIN 角色；无变化返回 0 */
    int grantAdminByCode(@Param("code") String code);

    long countUsersWithRole(String roleCode);

    int insertUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);

    int deleteUserRoles(Long userId);

    int insertRolePermission(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);

    int deleteRolePermissions(Long roleId);

    int insertRole(SysRole role);

    int deleteRole(Long id);
}

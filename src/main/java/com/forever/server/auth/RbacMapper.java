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

    long countUsersWithRole(String roleCode);

    int insertUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);

    int deleteUserRoles(Long userId);

    int insertRolePermission(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);

    int deleteRolePermissions(Long roleId);

    int insertRole(SysRole role);

    int deleteRole(Long id);
}

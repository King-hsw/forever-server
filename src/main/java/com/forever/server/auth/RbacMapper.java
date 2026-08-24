package com.forever.server.auth;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RbacMapper {

    // ---------- 查询 ----------

    @Select("""
            SELECT p.* FROM sys_permission p
            JOIN sys_role_permission rp ON rp.permission_id = p.id
            JOIN sys_user_role ur ON ur.role_id = rp.role_id
            JOIN sys_user u ON u.id = ur.user_id AND u.status = 'ACTIVE'
            WHERE ur.user_id = #{uid}
            """)
    List<SysPermission> permissionsByUserId(Long uid);

    @Select("SELECT role_id FROM sys_user_role WHERE user_id = #{uid}")
    List<Long> roleIdsByUserId(Long uid);

    @Select("""
            SELECT r.* FROM sys_role r ORDER BY r.id
            """)
    List<SysRole> listRoles();

    @Select("SELECT * FROM sys_role WHERE code = #{code}")
    SysRole findRoleByCode(String code);

    @Select("SELECT * FROM sys_role WHERE id = #{id}")
    SysRole findRoleById(Long id);

    @Select("SELECT permission_id FROM sys_role_permission WHERE role_id = #{roleId}")
    List<Long> permissionIdsByRoleId(Long roleId);

    @Select("SELECT * FROM sys_permission ORDER BY module, id")
    List<SysPermission> listPermissions();

    // ---------- 用户-角色 ----------

    @Select("""
            SELECT COUNT(*) FROM sys_user_role ur
            JOIN sys_role r ON r.id = ur.role_id AND r.code = #{roleCode}
            """)
    long countUsersWithRole(String roleCode);

    @Insert("INSERT INTO sys_user_role (user_id, role_id) VALUES (#{userId}, #{roleId}) " +
            "ON CONFLICT DO NOTHING")
    int insertUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);

    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    int deleteUserRoles(Long userId);

    // ---------- 角色-权限 ----------

    @Insert("INSERT INTO sys_role_permission (role_id, permission_id) VALUES (#{roleId}, #{permissionId}) " +
            "ON CONFLICT DO NOTHING")
    int insertRolePermission(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);

    @Delete("DELETE FROM sys_role_permission WHERE role_id = #{roleId}")
    int deleteRolePermissions(Long roleId);

    // ---------- 角色维护 ----------

    @Insert("INSERT INTO sys_role (code, name, remark) VALUES (#{code}, #{name}, #{remark})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertRole(SysRole role);

    @Delete("DELETE FROM sys_role WHERE id = #{id} AND built_in = false")
    int deleteRole(Long id);
}

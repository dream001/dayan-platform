package com.dayan.platform.repository.mapper;

import com.dayan.platform.model.RolePermission;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface RolePermissionMapper {

    @Insert("""
            INSERT INTO sys_role_permission (role_id, permission_id)
            VALUES (#{roleId}, #{permissionId})
            """)
    int insert(RolePermission rolePermission);

    @Delete("DELETE FROM sys_role_permission WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") long roleId);

    @Insert("""
            <script>
            INSERT INTO sys_role_permission (role_id, permission_id)
            VALUES
            <foreach collection="permissionIds" item="permissionId" separator=",">
                (#{roleId}, #{permissionId})
            </foreach>
            </script>
            """)
    int insertPermissions(
            @Param("roleId") long roleId,
            @Param("permissionIds") Collection<Long> permissionIds
    );

    @Select("""
            SELECT permission_id
            FROM sys_role_permission
            WHERE role_id = #{roleId}
            ORDER BY permission_id
            """)
    List<Long> selectPermissionIdsByRoleId(@Param("roleId") long roleId);

    @Select("SELECT count(*) FROM sys_role_permission WHERE permission_id = #{permissionId}")
    long countByPermissionId(@Param("permissionId") long permissionId);
}

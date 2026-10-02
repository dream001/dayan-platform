package com.dayan.platform.repository.mapper;

import com.dayan.platform.model.UserRole;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface UserRoleMapper {

    @Insert("""
            INSERT INTO sys_user_role (user_id, role_id, assigned_by)
            VALUES (#{userId}, #{roleId}, #{assignedBy})
            """)
    int insert(UserRole userRole);

    @Select("""
            SELECT count(*)
            FROM sys_user_role
            WHERE user_id = #{userId}
              AND role_id = #{roleId}
            """)
    long countByUserAndRole(@Param("userId") long userId, @Param("roleId") long roleId);

    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") long userId);

    @Delete("DELETE FROM sys_user_role WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") long roleId);

    @Insert("""
            <script>
            INSERT INTO sys_user_role (user_id, role_id, assigned_by)
            VALUES
            <foreach collection="roleIds" item="roleId" separator=",">
                (#{userId}, #{roleId}, #{assignedBy})
            </foreach>
            </script>
            """)
    int insertRoles(
            @Param("userId") long userId,
            @Param("roleIds") Collection<Long> roleIds,
            @Param("assignedBy") long assignedBy
    );

    @Insert("""
            <script>
            INSERT INTO sys_user_role (user_id, role_id, assigned_by)
            VALUES
            <foreach collection="userIds" item="userId" separator=",">
                (#{userId}, #{roleId}, #{assignedBy})
            </foreach>
            </script>
            """)
    int insertUsers(
            @Param("roleId") long roleId,
            @Param("userIds") Collection<Long> userIds,
            @Param("assignedBy") long assignedBy
    );

    @Select("SELECT role_id FROM sys_user_role WHERE user_id = #{userId} ORDER BY role_id")
    List<Long> selectRoleIdsByUserId(@Param("userId") long userId);

    @Select("SELECT user_id FROM sys_user_role WHERE role_id = #{roleId} ORDER BY user_id")
    List<Long> selectUserIdsByRoleId(@Param("roleId") long roleId);

    @Select("SELECT count(*) FROM sys_user_role WHERE role_id = #{roleId}")
    long countByRoleId(@Param("roleId") long roleId);
}

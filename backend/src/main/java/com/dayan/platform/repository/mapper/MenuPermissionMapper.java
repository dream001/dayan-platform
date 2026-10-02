package com.dayan.platform.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dayan.platform.model.MenuPermission;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface MenuPermissionMapper extends BaseMapper<MenuPermission> {

    @Select("SELECT count(*) FROM sys_menu_permission WHERE parent_id = #{id}")
    long countChildren(@Param("id") long id);

    @Select("""
            WITH RECURSIVE descendants AS (
                SELECT id FROM sys_menu_permission WHERE parent_id = #{id}
                UNION ALL
                SELECT m.id
                FROM sys_menu_permission m
                JOIN descendants x ON m.parent_id = x.id
            )
            SELECT count(*) FROM descendants WHERE id = #{candidateParentId}
            """)
    long countDescendant(
            @Param("id") long id,
            @Param("candidateParentId") long candidateParentId
    );

    @Select("""
            <script>
            SELECT *
            FROM sys_menu_permission
            <where>
              <choose>
                <when test="parentId == null">parent_id IS NULL</when>
                <otherwise>parent_id = #{parentId}</otherwise>
              </choose>
            </where>
            ORDER BY sort_order, id
            FOR UPDATE
            </script>
            """)
    List<MenuPermission> selectSiblingsForUpdate(@Param("parentId") Long parentId);

    @Select("""
            WITH RECURSIVE granted AS (
                SELECT DISTINCT p.id, p.parent_id
                FROM sys_user_role ur
                JOIN sys_role r ON r.id = ur.role_id AND r.enabled = TRUE
                JOIN sys_role_permission rp ON rp.role_id = r.id
                JOIN sys_menu_permission p ON p.id = rp.permission_id
                WHERE ur.user_id = #{userId}
                  AND p.type = 'MENU'
                  AND p.enabled = TRUE
                  AND p.visible = TRUE
            ),
            accessible AS (
                SELECT id, parent_id FROM granted
                UNION
                SELECT p.id, p.parent_id
                FROM sys_menu_permission p
                JOIN accessible a ON a.parent_id = p.id
                WHERE p.type = 'MENU'
                  AND p.enabled = TRUE
                  AND p.visible = TRUE
            )
            SELECT p.*
            FROM sys_menu_permission p
            JOIN accessible a ON a.id = p.id
            ORDER BY p.sort_order, p.id
            """)
    List<MenuPermission> selectAccessibleMenus(@Param("userId") long userId);

    @Select("""
            <script>
            SELECT count(*) FROM sys_menu_permission
            WHERE id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    long countByIds(@Param("ids") Collection<Long> ids);
}

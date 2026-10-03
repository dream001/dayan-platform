package com.dayan.platform.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dayan.platform.model.UserAccount;
import com.dayan.platform.repository.query.OptionRow;
import com.dayan.platform.repository.query.UserSummaryRow;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface UserAccountMapper extends BaseMapper<UserAccount> {

    @Select("""
            SELECT DISTINCT p.code
            FROM sys_user_role ur
            JOIN sys_role r ON r.id = ur.role_id AND r.enabled = TRUE
            JOIN sys_role_permission rp ON rp.role_id = r.id
            JOIN sys_menu_permission p ON p.id = rp.permission_id AND p.enabled = TRUE
            WHERE ur.user_id = #{userId}
              AND p.code IS NOT NULL
            ORDER BY p.code
            """)
    List<String> selectPermissionCodes(@Param("userId") long userId);

    @Select("""
            <script>
            SELECT u.id, u.department_id, d.name AS department_name, u.username,
                   u.display_name, u.email, u.phone, u.enabled, u.last_login_at, u.created_at,
                   COALESCE(
                       jsonb_agg(
                           jsonb_build_object('id', r.id, 'name', r.name, 'code', r.code, 'enabled', r.enabled)
                           ORDER BY r.name
                       ) FILTER (WHERE r.id IS NOT NULL),
                       '[]'::jsonb
                   )::text AS roles_json
            FROM sys_user u
            LEFT JOIN sys_department d ON d.id = u.department_id
            LEFT JOIN sys_user_role ur ON ur.user_id = u.id
            LEFT JOIN sys_role r ON r.id = ur.role_id
            <where>
              <if test="keyword != null and keyword != ''">
                AND (u.username ILIKE '%' || #{keyword} || '%'
                     OR u.display_name ILIKE '%' || #{keyword} || '%'
                     OR u.email ILIKE '%' || #{keyword} || '%')
              </if>
              <if test="departmentId != null">AND u.department_id = #{departmentId}</if>
              <if test="enabled != null">AND u.enabled = #{enabled}</if>
              <if test="roleCode != null and roleCode != ''">
                <choose>
                  <when test='"VISITOR".equals(roleCode)'>
                    AND NOT EXISTS (
                      SELECT 1 FROM sys_user_role visitor_role
                      WHERE visitor_role.user_id = u.id
                    )
                  </when>
                  <otherwise>
                    AND EXISTS (
                      SELECT 1
                      FROM sys_user_role filter_user_role
                      JOIN sys_role filter_role ON filter_role.id = filter_user_role.role_id
                      WHERE filter_user_role.user_id = u.id
                        AND filter_role.code = #{roleCode}
                    )
                  </otherwise>
                </choose>
              </if>
              <if test="projectId != null">
                AND EXISTS (
                  SELECT 1
                  FROM basic_project_member project_member
                  WHERE project_member.user_id = u.id
                    AND project_member.project_id = #{projectId}
                    AND (project_member.valid_from IS NULL
                         OR project_member.valid_from &lt;= CURRENT_TIMESTAMP)
                    AND (project_member.valid_until IS NULL
                         OR project_member.valid_until > CURRENT_TIMESTAMP)
                )
              </if>
            </where>
            GROUP BY u.id, d.name
            ORDER BY u.created_at DESC, u.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<UserSummaryRow> selectSummaryPage(
            @Param("keyword") String keyword,
            @Param("departmentId") Long departmentId,
            @Param("enabled") Boolean enabled,
            @Param("roleCode") String roleCode,
            @Param("projectId") Long projectId,
            @Param("offset") long offset,
            @Param("limit") int limit
    );

    @Select("""
            <script>
            SELECT count(*)
            FROM sys_user u
            <where>
              <if test="keyword != null and keyword != ''">
                AND (u.username ILIKE '%' || #{keyword} || '%'
                     OR u.display_name ILIKE '%' || #{keyword} || '%'
                     OR u.email ILIKE '%' || #{keyword} || '%')
              </if>
              <if test="departmentId != null">AND u.department_id = #{departmentId}</if>
              <if test="enabled != null">AND u.enabled = #{enabled}</if>
              <if test="roleCode != null and roleCode != ''">
                <choose>
                  <when test='"VISITOR".equals(roleCode)'>
                    AND NOT EXISTS (
                      SELECT 1 FROM sys_user_role visitor_role
                      WHERE visitor_role.user_id = u.id
                    )
                  </when>
                  <otherwise>
                    AND EXISTS (
                      SELECT 1
                      FROM sys_user_role filter_user_role
                      JOIN sys_role filter_role ON filter_role.id = filter_user_role.role_id
                      WHERE filter_user_role.user_id = u.id
                        AND filter_role.code = #{roleCode}
                    )
                  </otherwise>
                </choose>
              </if>
              <if test="projectId != null">
                AND EXISTS (
                  SELECT 1
                  FROM basic_project_member project_member
                  WHERE project_member.user_id = u.id
                    AND project_member.project_id = #{projectId}
                    AND (project_member.valid_from IS NULL
                         OR project_member.valid_from &lt;= CURRENT_TIMESTAMP)
                    AND (project_member.valid_until IS NULL
                         OR project_member.valid_until > CURRENT_TIMESTAMP)
                )
              </if>
            </where>
            </script>
            """)
    long countSummaries(
            @Param("keyword") String keyword,
            @Param("departmentId") Long departmentId,
            @Param("enabled") Boolean enabled,
            @Param("roleCode") String roleCode,
            @Param("projectId") Long projectId
    );

    @Select("""
            SELECT id, name
            FROM basic_project
            WHERE status <> 'ARCHIVED'
            ORDER BY name, id
            """)
    List<OptionRow> selectProjectOptions();

    @Select("""
            SELECT u.id, u.department_id, d.name AS department_name, u.username,
                   u.display_name, u.email, u.phone, u.enabled, u.last_login_at, u.created_at,
                   COALESCE(
                       jsonb_agg(
                           jsonb_build_object('id', r.id, 'name', r.name, 'code', r.code, 'enabled', r.enabled)
                           ORDER BY r.name
                       ) FILTER (WHERE r.id IS NOT NULL),
                       '[]'::jsonb
                   )::text AS roles_json
            FROM sys_user u
            LEFT JOIN sys_department d ON d.id = u.department_id
            LEFT JOIN sys_user_role ur ON ur.user_id = u.id
            LEFT JOIN sys_role r ON r.id = ur.role_id
            WHERE u.id = #{id}
            GROUP BY u.id, d.name
            """)
    UserSummaryRow selectSummaryById(@Param("id") long id);

    @Select("""
            <script>
            SELECT count(*) FROM sys_user
            WHERE id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    long countByIds(@Param("ids") Collection<Long> ids);
}

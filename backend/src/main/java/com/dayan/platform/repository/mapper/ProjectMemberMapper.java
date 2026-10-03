package com.dayan.platform.repository.mapper;

import com.dayan.platform.repository.query.ProjectMemberRow;
import com.dayan.platform.vo.ProjectViews.ProjectUserOption;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ProjectMemberMapper {

    @Select("""
            SELECT pm.user_id, u.username, u.display_name, pm.role, pm.data_access_level,
                   pm.valid_from, pm.valid_until,
                   ((pm.valid_from IS NULL OR pm.valid_from <= CURRENT_TIMESTAMP)
                    AND (pm.valid_until IS NULL OR pm.valid_until > CURRENT_TIMESTAMP)) AS active
            FROM basic_project_member pm
            JOIN sys_user u ON u.id = pm.user_id
            WHERE pm.project_id = #{projectId}
            ORDER BY
              CASE pm.role
                WHEN 'PROJECT_ADMIN' THEN 1
                WHEN 'PROJECT_MANAGER' THEN 2
                WHEN 'REVIEWER' THEN 3
                WHEN 'ANNOTATOR' THEN 4
                ELSE 5
              END,
              u.display_name,
              u.id
            """)
    List<ProjectMemberRow> selectMembers(@Param("projectId") long projectId);

    @Select("""
            SELECT role
            FROM basic_project_member
            WHERE project_id = #{projectId}
              AND user_id = #{userId}
              AND (valid_from IS NULL OR valid_from <= CURRENT_TIMESTAMP)
              AND (valid_until IS NULL OR valid_until > CURRENT_TIMESTAMP)
            """)
    String selectActiveRole(
            @Param("projectId") long projectId,
            @Param("userId") long userId
    );

    @Select("""
            SELECT role
            FROM basic_project_member
            WHERE project_id = #{projectId}
              AND user_id = #{userId}
            """)
    String selectRole(
            @Param("projectId") long projectId,
            @Param("userId") long userId
    );

    @Select("SELECT count(*) FROM basic_project_member WHERE project_id = #{projectId}")
    long countAll(@Param("projectId") long projectId);

    @Select("""
            SELECT count(*)
            FROM basic_project_member
            WHERE project_id = #{projectId}
              AND role = 'PROJECT_ADMIN'
            """)
    long countAdministrators(@Param("projectId") long projectId);

    @Insert("""
            INSERT INTO basic_project_member
                (project_id, user_id, role, data_access_level, valid_from, valid_until, assigned_by)
            VALUES
                (#{projectId}, #{userId}, #{role}, #{dataAccessLevel},
                 #{validFrom}, #{validUntil}, #{assignedBy})
            ON CONFLICT (project_id, user_id) DO UPDATE
            SET role = EXCLUDED.role,
                data_access_level = EXCLUDED.data_access_level,
                valid_from = EXCLUDED.valid_from,
                valid_until = EXCLUDED.valid_until,
                assigned_by = EXCLUDED.assigned_by,
                updated_at = CURRENT_TIMESTAMP
            """)
    int upsert(
            @Param("projectId") long projectId,
            @Param("userId") long userId,
            @Param("role") String role,
            @Param("dataAccessLevel") String dataAccessLevel,
            @Param("validFrom") OffsetDateTime validFrom,
            @Param("validUntil") OffsetDateTime validUntil,
            @Param("assignedBy") long assignedBy
    );

    @Delete("""
            DELETE FROM basic_project_member
            WHERE project_id = #{projectId}
              AND user_id = #{userId}
            """)
    int delete(
            @Param("projectId") long projectId,
            @Param("userId") long userId
    );

    @Select("""
            <script>
            SELECT
              u.id,
              u.username,
              u.display_name,
              COALESCE(
                #{personnelType},
                (
                  SELECT role.code
                  FROM sys_user_role user_role
                  JOIN sys_role role ON role.id = user_role.role_id AND role.enabled = TRUE
                  WHERE user_role.user_id = u.id
                  ORDER BY
                    CASE role.code
                      WHEN 'SUPER_ADMIN' THEN 1
                      WHEN 'MANAGER' THEN 2
                      WHEN 'COLLECTOR' THEN 3
                      WHEN 'ANNOTATOR' THEN 4
                      WHEN 'AUDITOR' THEN 5
                      ELSE 6
                    END,
                    role.id
                  LIMIT 1
                ),
                'GUEST'
              ) AS personnel_type
            FROM sys_user u
            WHERE u.enabled = TRUE
              <choose>
                <when test="personnelType == 'GUEST'">
                  AND NOT EXISTS (
                    SELECT 1
                    FROM sys_user_role user_role
                    JOIN sys_role role ON role.id = user_role.role_id AND role.enabled = TRUE
                    WHERE user_role.user_id = u.id
                  )
                </when>
                <when test="personnelType != null and personnelType != ''">
                  AND EXISTS (
                    SELECT 1
                    FROM sys_user_role user_role
                    JOIN sys_role role ON role.id = user_role.role_id AND role.enabled = TRUE
                    WHERE user_role.user_id = u.id
                      AND role.code = #{personnelType}
                  )
                </when>
              </choose>
              <if test="keyword != null and keyword != ''">
                AND (u.username ILIKE '%' || #{keyword} || '%'
                     OR u.display_name ILIKE '%' || #{keyword} || '%')
              </if>
            ORDER BY u.display_name, u.id
            LIMIT 50
            </script>
            """)
    List<ProjectUserOption> selectUserOptions(
            @Param("keyword") String keyword,
            @Param("personnelType") String personnelType
    );
}

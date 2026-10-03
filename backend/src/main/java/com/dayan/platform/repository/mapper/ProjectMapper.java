package com.dayan.platform.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dayan.platform.model.Project;
import com.dayan.platform.repository.query.ProjectMetricsRow;
import com.dayan.platform.repository.query.ProjectSummaryRow;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ProjectMapper extends BaseMapper<Project> {

    @Select("""
            <script>
            SELECT p.*,
                   owner.display_name AS owner_name,
                   count(DISTINCT member.user_id) AS member_count,
                   current_member.role AS current_role
            FROM basic_project p
            JOIN sys_user owner ON owner.id = p.owner_id
            LEFT JOIN basic_project_member member ON member.project_id = p.id
            LEFT JOIN basic_project_member current_member
              ON current_member.project_id = p.id
             AND current_member.user_id = #{userId}
             AND (current_member.valid_from IS NULL OR current_member.valid_from &lt;= CURRENT_TIMESTAMP)
             AND (current_member.valid_until IS NULL OR current_member.valid_until > CURRENT_TIMESTAMP)
            <where>
              AND (#{platformAdmin} = TRUE OR p.access_level = 'PUBLIC' OR current_member.user_id IS NOT NULL)
              <if test="keyword != null and keyword != ''">
                AND (p.name ILIKE '%' || #{keyword} || '%'
                     OR p.code ILIKE '%' || #{keyword} || '%')
              </if>
              <if test="status != null and status != ''">AND p.status = #{status}</if>
              <if test="projectType != null and projectType != ''">AND p.project_type = #{projectType}</if>
            </where>
            GROUP BY p.id, owner.display_name, current_member.role
            ORDER BY
              CASE p.status
                WHEN 'ACTIVE' THEN 1
                WHEN 'PLANNING' THEN 2
                WHEN 'SUSPENDED' THEN 3
                WHEN 'COMPLETED' THEN 4
                ELSE 5
              END,
              p.updated_at DESC,
              p.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<ProjectSummaryRow> selectSummaryPage(
            @Param("userId") long userId,
            @Param("platformAdmin") boolean platformAdmin,
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("projectType") String projectType,
            @Param("offset") long offset,
            @Param("limit") int limit
    );

    @Select("""
            <script>
            SELECT count(*)
            FROM basic_project p
            WHERE (#{platformAdmin} = TRUE
                   OR p.access_level = 'PUBLIC'
                   OR EXISTS (
                       SELECT 1
                       FROM basic_project_member pm
                       WHERE pm.project_id = p.id
                         AND pm.user_id = #{userId}
                         AND (pm.valid_from IS NULL OR pm.valid_from &lt;= CURRENT_TIMESTAMP)
                         AND (pm.valid_until IS NULL OR pm.valid_until > CURRENT_TIMESTAMP)
                   ))
              <if test="keyword != null and keyword != ''">
                AND (p.name ILIKE '%' || #{keyword} || '%'
                     OR p.code ILIKE '%' || #{keyword} || '%')
              </if>
              <if test="status != null and status != ''">AND p.status = #{status}</if>
              <if test="projectType != null and projectType != ''">AND p.project_type = #{projectType}</if>
            </script>
            """)
    long countAccessible(
            @Param("userId") long userId,
            @Param("platformAdmin") boolean platformAdmin,
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("projectType") String projectType
    );

    @Select("""
            SELECT p.*,
                   owner.display_name AS owner_name,
                   count(DISTINCT member.user_id) AS member_count,
                   current_member.role AS current_role
            FROM basic_project p
            JOIN sys_user owner ON owner.id = p.owner_id
            LEFT JOIN basic_project_member member ON member.project_id = p.id
            LEFT JOIN basic_project_member current_member
              ON current_member.project_id = p.id
             AND current_member.user_id = #{userId}
             AND (current_member.valid_from IS NULL OR current_member.valid_from <= CURRENT_TIMESTAMP)
             AND (current_member.valid_until IS NULL OR current_member.valid_until > CURRENT_TIMESTAMP)
            WHERE p.id = #{id}
            GROUP BY p.id, owner.display_name, current_member.role
            """)
    ProjectSummaryRow selectSummaryById(
            @Param("id") long id,
            @Param("userId") long userId
    );

    @Select("""
            SELECT
              (SELECT count(*) FROM data_dataset d
               WHERE d.project_id = #{projectId} AND d.deleted = FALSE) AS dataset_count,
              (SELECT count(*) FROM data_dataset d
               WHERE d.project_id = #{projectId} AND d.deleted = FALSE
                 AND d.data_type = 'VIDEO') AS video_count,
              (SELECT count(*) FROM data_dataset d
               WHERE d.project_id = #{projectId} AND d.deleted = FALSE
                 AND d.data_type = 'AUDIO') AS audio_count,
              (SELECT count(*) FROM data_dataset d
               WHERE d.project_id = #{projectId} AND d.deleted = FALSE
                 AND d.data_type = 'MCAP') AS mcap_count,
              (SELECT coalesce(sum(d.size_bytes), 0) FROM data_dataset d
               WHERE d.project_id = #{projectId} AND d.deleted = FALSE) AS storage_used_bytes,
              (SELECT count(*) FROM data_annotation_task t
               WHERE t.project_id = #{projectId} AND t.deleted_at IS NULL) AS annotation_task_count,
              (SELECT count(*) FROM data_collection_task t
               WHERE t.project_id = #{projectId} AND t.deleted_at IS NULL) AS collection_task_count,
              ((SELECT count(*) FROM data_annotation_task t
                WHERE t.project_id = #{projectId} AND t.deleted_at IS NULL
                  AND t.status IN ('APPROVED', 'SUBMITTED'))
               +
               (SELECT count(*) FROM data_collection_task t
                WHERE t.project_id = #{projectId} AND t.deleted_at IS NULL
                  AND t.status IN ('APPROVED', 'SUBMITTED'))) AS completed_task_count,
              (SELECT coalesce(
                   round(100.0 * count(*) FILTER (WHERE a.is_qualified) / nullif(count(*), 0), 2),
                   0
               )
               FROM data_annotation a
               JOIN data_dataset d ON d.id = a.dataset_id
               WHERE d.project_id = #{projectId} AND d.deleted = FALSE
                 AND a.reviewed = TRUE) AS quality_rate,
              (SELECT count(*) FROM basic_project_member pm
               WHERE pm.project_id = #{projectId}
                 AND (pm.valid_from IS NULL OR pm.valid_from <= CURRENT_TIMESTAMP)
                 AND (pm.valid_until IS NULL OR pm.valid_until > CURRENT_TIMESTAMP))
                 AS active_member_count
            """)
    ProjectMetricsRow selectMetrics(@Param("projectId") long projectId);
}

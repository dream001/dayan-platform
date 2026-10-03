package com.dayan.platform.repository.mapper;

import com.dayan.platform.repository.query.ChartQueryRows.CalendarRow;
import com.dayan.platform.repository.query.ChartQueryRows.DurationRow;
import com.dayan.platform.repository.query.ChartQueryRows.LinkRow;
import com.dayan.platform.repository.query.ChartQueryRows.RelationshipRow;
import com.dayan.platform.vo.ChartViews.ProjectOption;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ChartMapper {

    @Select("""
            SELECT p.id, p.code, p.name
            FROM basic_project p
            WHERE #{platformAdmin} = TRUE
               OR p.access_level = 'PUBLIC'
               OR EXISTS (
                   SELECT 1
                   FROM basic_project_member pm
                   WHERE pm.project_id = p.id
                     AND pm.user_id = #{userId}
                     AND (pm.valid_from IS NULL OR pm.valid_from <= CURRENT_TIMESTAMP)
                     AND (pm.valid_until IS NULL OR pm.valid_until > CURRENT_TIMESTAMP)
               )
            ORDER BY
              CASE p.status WHEN 'ACTIVE' THEN 1 WHEN 'PLANNING' THEN 2 ELSE 3 END,
              p.name,
              p.id
            """)
    List<ProjectOption> selectProjectOptions(
            @Param("userId") long userId,
            @Param("platformAdmin") boolean platformAdmin
    );

    @Select("""
            SELECT count(*)
            FROM basic_project p
            WHERE p.id = #{projectId}
              AND (
                #{platformAdmin} = TRUE
                OR p.access_level = 'PUBLIC'
                OR EXISTS (
                    SELECT 1
                    FROM basic_project_member pm
                    WHERE pm.project_id = p.id
                      AND pm.user_id = #{userId}
                      AND (pm.valid_from IS NULL OR pm.valid_from <= CURRENT_TIMESTAMP)
                      AND (pm.valid_until IS NULL OR pm.valid_until > CURRENT_TIMESTAMP)
                )
              )
            """)
    long countAccessibleProject(
            @Param("projectId") long projectId,
            @Param("userId") long userId,
            @Param("platformAdmin") boolean platformAdmin
    );

    @Select("""
            SELECT
              CASE WHEN #{localized} = TRUE
                   THEN COALESCE(
                     (
                       SELECT entry.chinese_text
                       FROM data_dictionary_entry entry
                       WHERE entry.dictionary_type = 'SKILL'
                         AND lower(entry.english_text) = lower(a.skill_name)
                         AND (entry.scope != 'PROJECT' OR entry.project_id = d.project_id)
                       ORDER BY
                         CASE entry.scope WHEN 'PROJECT' THEN 1 WHEN 'GLOBAL' THEN 2 ELSE 3 END,
                         entry.id
                       LIMIT 1
                     ),
                     NULLIF(a.skill_name_zh, ''),
                     a.skill_name
                   )
                   ELSE a.skill_name END AS skill_name,
              CASE WHEN #{localized} = TRUE
                   THEN COALESCE(
                     (
                       SELECT entry.chinese_text
                       FROM data_dictionary_entry entry
                       WHERE entry.dictionary_type = 'OBJECT'
                         AND lower(entry.english_text) = lower(a.object_a_name)
                         AND (entry.scope != 'PROJECT' OR entry.project_id = d.project_id)
                       ORDER BY
                         CASE entry.scope WHEN 'PROJECT' THEN 1 WHEN 'GLOBAL' THEN 2 ELSE 3 END,
                         entry.id
                       LIMIT 1
                     ),
                     NULLIF(a.object_a_name_zh, ''),
                     a.object_a_name
                   )
                   ELSE a.object_a_name END AS object_a_name,
              CASE WHEN #{localized} = TRUE
                   THEN COALESCE(
                     (
                       SELECT entry.chinese_text
                       FROM data_dictionary_entry entry
                       WHERE entry.dictionary_type = 'OBJECT'
                         AND lower(entry.english_text) = lower(a.object_b_name)
                         AND (entry.scope != 'PROJECT' OR entry.project_id = d.project_id)
                       ORDER BY
                         CASE entry.scope WHEN 'PROJECT' THEN 1 WHEN 'GLOBAL' THEN 2 ELSE 3 END,
                         entry.id
                       LIMIT 1
                     ),
                     NULLIF(a.object_b_name_zh, ''),
                     a.object_b_name
                   )
                   ELSE a.object_b_name END AS object_b_name,
              count(*) AS marker_count
            FROM data_annotation a
            JOIN data_dataset d ON d.id = a.dataset_id
            WHERE d.project_id = #{projectId}
              AND d.deleted = FALSE
              AND a.is_valid = TRUE
              AND NULLIF(btrim(a.skill_name), '') IS NOT NULL
              AND NULLIF(btrim(a.object_a_name), '') IS NOT NULL
            GROUP BY 1, 2, 3
            ORDER BY marker_count DESC, skill_name, object_a_name, object_b_name
            """)
    List<RelationshipRow> selectRelationships(
            @Param("projectId") long projectId,
            @Param("localized") boolean localized
    );

    @Select("""
            WITH base AS (
              SELECT
                a.dataset_id,
                a.id,
                COALESCE(a.start_offset_seconds, 999999999999) AS position,
                CASE WHEN #{localized} = TRUE
                     THEN COALESCE(
                       (
                         SELECT entry.chinese_text
                         FROM data_dictionary_entry entry
                         WHERE entry.dictionary_type IN ('SKILL', 'OBJECT')
                           AND lower(entry.english_text) = lower(btrim(a.action_name))
                           AND (entry.scope != 'PROJECT' OR entry.project_id = d.project_id)
                         ORDER BY
                           CASE entry.scope WHEN 'PROJECT' THEN 1 WHEN 'GLOBAL' THEN 2 ELSE 3 END,
                           entry.id
                         LIMIT 1
                       ),
                       btrim(a.action_name)
                     )
                     ELSE btrim(a.action_name) END AS action_name,
                row_number() OVER (
                  PARTITION BY a.dataset_id, btrim(a.action_name)
                  ORDER BY COALESCE(a.start_offset_seconds, 999999999999), a.id
                ) AS occurrence
              FROM data_annotation a
              JOIN data_dataset d ON d.id = a.dataset_id
              WHERE d.project_id = #{projectId}
                AND d.deleted = FALSE
                AND a.is_valid = TRUE
                AND NULLIF(btrim(a.action_name), '') IS NOT NULL
            ),
            named AS (
              SELECT
                dataset_id,
                id,
                position,
                CASE WHEN occurrence = 1
                     THEN action_name
                     ELSE action_name || ' #' || occurrence END AS action_name
              FROM base
            ),
            adjacent AS (
              SELECT
                action_name AS source,
                lead(action_name) OVER (
                  PARTITION BY dataset_id ORDER BY position, id
                ) AS target
              FROM named
            )
            SELECT source, target, count(*) AS link_count
            FROM adjacent
            WHERE target IS NOT NULL
            GROUP BY source, target
            ORDER BY link_count DESC, source, target
            """)
    List<LinkRow> selectPlanningLinks(
            @Param("projectId") long projectId,
            @Param("localized") boolean localized
    );

    @Select("""
            SELECT
              btrim(a.action_name) AS action_name,
              round(avg(
                COALESCE(
                  CASE
                    WHEN a.start_offset_seconds IS NOT NULL
                     AND a.end_offset_seconds IS NOT NULL
                    THEN a.end_offset_seconds - a.start_offset_seconds
                  END,
                  a.covered_duration_seconds
                )
              ), 3) AS average_seconds,
              count(*) AS marker_count
            FROM data_annotation a
            JOIN data_dataset d ON d.id = a.dataset_id
            WHERE d.project_id = #{projectId}
              AND d.deleted = FALSE
              AND a.is_valid = TRUE
              AND NULLIF(btrim(a.action_name), '') IS NOT NULL
              AND COALESCE(
                    CASE
                      WHEN a.start_offset_seconds IS NOT NULL
                       AND a.end_offset_seconds IS NOT NULL
                      THEN a.end_offset_seconds - a.start_offset_seconds
                    END,
                    a.covered_duration_seconds
                  ) IS NOT NULL
            GROUP BY btrim(a.action_name)
            ORDER BY average_seconds DESC, action_name
            """)
    List<DurationRow> selectDurations(@Param("projectId") long projectId);

    @Select("""
            WITH ordered AS (
              SELECT
                a.dataset_id,
                a.id,
                COALESCE(a.start_offset_seconds, 999999999999) AS position,
                btrim(a.content_text) AS description,
                lead(btrim(a.content_text)) OVER (
                  PARTITION BY a.dataset_id
                  ORDER BY COALESCE(a.start_offset_seconds, 999999999999), a.id
                ) AS next_description
              FROM data_annotation a
              JOIN data_dataset d ON d.id = a.dataset_id
              WHERE d.project_id = #{projectId}
                AND d.deleted = FALSE
                AND a.is_valid = TRUE
                AND NULLIF(btrim(a.content_text), '') IS NOT NULL
            )
            SELECT description AS source, next_description AS target, count(*) AS link_count
            FROM ordered
            WHERE next_description IS NOT NULL
            GROUP BY description, next_description
            ORDER BY link_count DESC, source, target
            """)
    List<LinkRow> selectDependencyLinks(@Param("projectId") long projectId);

    @Select("""
            SELECT
              (a.created_at AT TIME ZONE 'UTC')::date AS annotation_date,
              count(*) AS marker_count
            FROM data_annotation a
            JOIN data_dataset d ON d.id = a.dataset_id
            WHERE d.project_id = #{projectId}
              AND d.deleted = FALSE
              AND a.is_valid = TRUE
            GROUP BY annotation_date
            ORDER BY annotation_date
            """)
    List<CalendarRow> selectCalendar(@Param("projectId") long projectId);
}

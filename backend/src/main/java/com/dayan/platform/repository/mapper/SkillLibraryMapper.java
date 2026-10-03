package com.dayan.platform.repository.mapper;

import com.dayan.platform.repository.query.SkillLibraryRows.SkillSampleRow;
import com.dayan.platform.repository.query.SkillLibraryRows.SkillSummaryRow;
import com.dayan.platform.vo.SkillLibraryViews.ProjectOption;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

public interface SkillLibraryMapper {

    String ACCESS = """
            AND (
              #{platformAdmin} = TRUE
              OR d.uploader_id = #{userId}
              OR EXISTS (
                SELECT 1
                FROM basic_project_member pm
                WHERE pm.project_id = d.project_id
                  AND pm.user_id = #{userId}
                  AND (pm.valid_from IS NULL OR pm.valid_from &lt;= CURRENT_TIMESTAMP)
                  AND (pm.valid_until IS NULL OR pm.valid_until > CURRENT_TIMESTAMP)
              )
              OR EXISTS (
                SELECT 1
                FROM basic_project access_project
                WHERE access_project.id = d.project_id
                  AND access_project.access_level = 'PUBLIC'
                  AND access_project.status = 'ACTIVE'
              )
            )
            """;

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
            ORDER BY p.name, p.id
            """)
    List<ProjectOption> selectProjectOptions(
            @Param("userId") long userId,
            @Param("platformAdmin") boolean platformAdmin
    );

    @Insert("""
            INSERT INTO data_skill (
                skill_key, name_zh, name_en, description, category, difficulty,
                status, current_version, usage_scene, tags, is_template
            )
            VALUES (
                #{skillKey}, #{nameZh}, #{nameEn}, #{description}, #{category},
                #{difficulty}, #{status}, #{currentVersion}, #{usageScene},
                string_to_array(#{tagsCsv}, ','), #{template}
            )
            ON CONFLICT (skill_key) DO UPDATE
            SET name_zh = EXCLUDED.name_zh,
                name_en = EXCLUDED.name_en,
                description = EXCLUDED.description,
                category = EXCLUDED.category,
                difficulty = EXCLUDED.difficulty,
                status = EXCLUDED.status,
                current_version = EXCLUDED.current_version,
                usage_scene = EXCLUDED.usage_scene,
                tags = EXCLUDED.tags,
                is_template = EXCLUDED.is_template,
                updated_at = CURRENT_TIMESTAMP
            """)
    int saveSkill(
            @Param("skillKey") String skillKey,
            @Param("nameZh") String nameZh,
            @Param("nameEn") String nameEn,
            @Param("description") String description,
            @Param("category") String category,
            @Param("difficulty") String difficulty,
            @Param("status") String status,
            @Param("currentVersion") String currentVersion,
            @Param("usageScene") String usageScene,
            @Param("tagsCsv") String tagsCsv,
            @Param("template") boolean template
    );

    @Insert("""
            INSERT INTO data_skill_version (
                skill_id, version, status, change_summary, definition_json, published_at
            )
            SELECT id, #{version},
                   CASE WHEN #{published} THEN 'PUBLISHED' ELSE 'DRAFT' END,
                   #{changeSummary}, '{}',
                   CASE WHEN #{published} THEN CURRENT_TIMESTAMP ELSE NULL END
            FROM data_skill
            WHERE skill_key = #{skillKey}
            ON CONFLICT (skill_id, version) DO NOTHING
            """)
    int insertVersion(
            @Param("skillKey") String skillKey,
            @Param("version") String version,
            @Param("published") boolean published,
            @Param("changeSummary") String changeSummary
    );

    @Select("""
            <script>
            WITH accessible_annotations AS (
              SELECT a.*, d.project_id
              FROM data_annotation a
              JOIN data_dataset d ON d.id = a.dataset_id
              WHERE d.deleted = FALSE
                AND NULLIF(btrim(a.skill_name), '') IS NOT NULL
                <if test="projectId != null">AND d.project_id = #{projectId}</if>
            """ + ACCESS + """
            )
            SELECT
              s.skill_key,
              CASE WHEN #{localized} = TRUE THEN s.name_zh ELSE s.name_en
              END AS display_name,
              s.description,
              s.category,
              s.difficulty,
              s.status,
              s.current_version,
              s.usage_scene,
              array_to_string(s.tags, ',') AS tags_csv,
              count(a.id) FILTER (WHERE a.is_valid) AS annotation_count,
              count(DISTINCT a.project_id) FILTER (WHERE a.is_valid) AS project_count,
              count(a.id) FILTER (
                WHERE a.is_valid
                  AND a.created_at >= CURRENT_TIMESTAMP - INTERVAL '30 days'
              ) AS recent_usage_count,
              (SELECT count(*) FROM data_skill_version v
               WHERE v.skill_id = s.id) AS version_count,
              (SELECT count(*) FROM data_skill_relation relation
               WHERE relation.source_skill_id = s.id) AS dependency_count,
              CASE WHEN count(a.id) = 0 THEN 0
                   ELSE round(
                     100.0 * count(a.id) FILTER (WHERE a.is_valid) / count(a.id),
                     2
                   )
              END AS quality_rate
            FROM data_skill s
            LEFT JOIN accessible_annotations a
              ON lower(btrim(a.skill_name)) = s.skill_key
            GROUP BY s.id
            <if test="projectId != null">HAVING count(a.id) > 0</if>
            ORDER BY annotation_count DESC, display_name, skill_key
            </script>
            """)
    List<SkillSummaryRow> selectSummaries(
            @Param("projectId") Long projectId,
            @Param("localized") boolean localized,
            @Param("userId") long userId,
            @Param("platformAdmin") boolean platformAdmin
    );

    @Select("""
            <script>
            WITH candidates AS (
              SELECT
                btrim(a.skill_name) AS skill_key,
                a.id AS annotation_id,
                d.id AS dataset_id,
                f.id AS file_id,
                COALESCE(NULLIF(btrim(a.content_text), ''), d.name) AS description,
                COALESCE(p.name, '-') AS project_name,
                d.data_type,
                f.content_type,
                CASE
                  WHEN lower(d.name) LIKE '%depth%'
                    OR lower(f.original_name) LIKE '%depth%'
                    OR EXISTS (
                      SELECT 1
                      FROM data_dataset_tag_rel relation
                      JOIN data_dataset_tag tag ON tag.id = relation.tag_id
                      WHERE relation.dataset_id = d.id
                        AND lower(tag.name) IN ('depth', 'depth image', '深度', '深度图')
                    )
                  THEN 'DEPTH'
                  ELSE 'COLOR'
                END AS media_type,
                a.created_at
              FROM data_annotation a
              JOIN data_dataset d ON d.id = a.dataset_id
              JOIN file_metadata f ON f.id = d.file_id AND f.status = 'READY'
              LEFT JOIN basic_project p ON p.id = d.project_id
              WHERE d.deleted = FALSE
                AND a.is_valid = TRUE
                AND NULLIF(btrim(a.skill_name), '') IS NOT NULL
                AND (f.content_type LIKE 'image/%' OR f.content_type LIKE 'video/%')
                <if test="projectId != null">AND d.project_id = #{projectId}</if>
            """ + ACCESS + """
            ),
            deduplicated AS (
              SELECT DISTINCT ON (lower(skill_key), lower(description))
                *
              FROM candidates
              ORDER BY lower(skill_key), lower(description), created_at DESC, annotation_id DESC
            ),
            ranked AS (
              SELECT
                *,
                row_number() OVER (
                  PARTITION BY lower(skill_key)
                  ORDER BY created_at DESC, annotation_id DESC
                ) AS sample_rank
              FROM deduplicated
            )
            SELECT
              skill_key,
              annotation_id,
              dataset_id,
              file_id,
              description,
              project_name,
              data_type,
              content_type,
              media_type,
              created_at
            FROM ranked
            WHERE sample_rank &lt;= 5
            ORDER BY lower(skill_key), sample_rank
            </script>
            """)
    List<SkillSampleRow> selectSummarySamples(
            @Param("projectId") Long projectId,
            @Param("userId") long userId,
            @Param("platformAdmin") boolean platformAdmin
    );

    @Select("""
            <script>
            SELECT count(*)
            FROM (
              SELECT DISTINCT ON (
                lower(COALESCE(NULLIF(btrim(a.content_text), ''), d.name))
              )
                a.id
              FROM data_annotation a
              JOIN data_dataset d ON d.id = a.dataset_id
              JOIN file_metadata f ON f.id = d.file_id AND f.status = 'READY'
              WHERE d.deleted = FALSE
                AND a.is_valid = TRUE
                AND lower(btrim(a.skill_name)) = lower(btrim(#{skillKey}))
                AND (f.content_type LIKE 'image/%' OR f.content_type LIKE 'video/%')
                AND (
                  CASE
                    WHEN lower(d.name) LIKE '%depth%'
                      OR lower(f.original_name) LIKE '%depth%'
                      OR EXISTS (
                        SELECT 1
                        FROM data_dataset_tag_rel relation
                        JOIN data_dataset_tag tag ON tag.id = relation.tag_id
                        WHERE relation.dataset_id = d.id
                          AND lower(tag.name) IN ('depth', 'depth image', '深度', '深度图')
                      )
                    THEN 'DEPTH'
                    ELSE 'COLOR'
                  END
                ) = #{mediaType}
                <if test="projectId != null">AND d.project_id = #{projectId}</if>
            """ + ACCESS + """
            ) sample
            </script>
            """)
    long countDetailSamples(
            @Param("skillKey") String skillKey,
            @Param("projectId") Long projectId,
            @Param("mediaType") String mediaType,
            @Param("userId") long userId,
            @Param("platformAdmin") boolean platformAdmin
    );

    @Select("""
            <script>
            WITH samples AS (
              SELECT DISTINCT ON (
                lower(COALESCE(NULLIF(btrim(a.content_text), ''), d.name))
              )
                btrim(a.skill_name) AS skill_key,
                a.id AS annotation_id,
                d.id AS dataset_id,
                f.id AS file_id,
                COALESCE(NULLIF(btrim(a.content_text), ''), d.name) AS description,
                COALESCE(p.name, '-') AS project_name,
                d.data_type,
                f.content_type,
                CASE
                  WHEN lower(d.name) LIKE '%depth%'
                    OR lower(f.original_name) LIKE '%depth%'
                    OR EXISTS (
                      SELECT 1
                      FROM data_dataset_tag_rel relation
                      JOIN data_dataset_tag tag ON tag.id = relation.tag_id
                      WHERE relation.dataset_id = d.id
                        AND lower(tag.name) IN ('depth', 'depth image', '深度', '深度图')
                    )
                  THEN 'DEPTH'
                  ELSE 'COLOR'
                END AS media_type,
                a.created_at
              FROM data_annotation a
              JOIN data_dataset d ON d.id = a.dataset_id
              JOIN file_metadata f ON f.id = d.file_id AND f.status = 'READY'
              LEFT JOIN basic_project p ON p.id = d.project_id
              WHERE d.deleted = FALSE
                AND a.is_valid = TRUE
                AND lower(btrim(a.skill_name)) = lower(btrim(#{skillKey}))
                AND (f.content_type LIKE 'image/%' OR f.content_type LIKE 'video/%')
                AND (
                  CASE
                    WHEN lower(d.name) LIKE '%depth%'
                      OR lower(f.original_name) LIKE '%depth%'
                      OR EXISTS (
                        SELECT 1
                        FROM data_dataset_tag_rel relation
                        JOIN data_dataset_tag tag ON tag.id = relation.tag_id
                        WHERE relation.dataset_id = d.id
                          AND lower(tag.name) IN ('depth', 'depth image', '深度', '深度图')
                      )
                    THEN 'DEPTH'
                    ELSE 'COLOR'
                  END
                ) = #{mediaType}
                <if test="projectId != null">AND d.project_id = #{projectId}</if>
            """ + ACCESS + """
              ORDER BY
                lower(COALESCE(NULLIF(btrim(a.content_text), ''), d.name)),
                a.created_at DESC,
                a.id DESC
            )
            SELECT *
            FROM samples
            ORDER BY created_at DESC, annotation_id DESC
            LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<SkillSampleRow> selectDetailSamples(
            @Param("skillKey") String skillKey,
            @Param("projectId") Long projectId,
            @Param("mediaType") String mediaType,
            @Param("userId") long userId,
            @Param("platformAdmin") boolean platformAdmin,
            @Param("size") int size,
            @Param("offset") long offset
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
}

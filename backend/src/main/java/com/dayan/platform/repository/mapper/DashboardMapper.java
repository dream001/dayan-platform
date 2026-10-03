package com.dayan.platform.repository.mapper;

import com.dayan.platform.repository.query.DashboardRows.BusinessSummaryRow;
import com.dayan.platform.repository.query.DashboardRows.NamedCountRow;
import com.dayan.platform.repository.query.DashboardRows.TrendRow;
import com.dayan.platform.repository.query.DashboardStatsRow;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface DashboardMapper {

    String ACCESSIBLE_PROJECT = """
            (#{platformAdmin} = TRUE
             OR project.access_level = 'PUBLIC'
             OR EXISTS (
                 SELECT 1
                 FROM basic_project_member member
                 WHERE member.project_id = project.id
                   AND member.user_id = #{userId}
                   AND (member.valid_from IS NULL OR member.valid_from <= CURRENT_TIMESTAMP)
                   AND (member.valid_until IS NULL OR member.valid_until > CURRENT_TIMESTAMP)
             ))
            """;

    @Select("""
            SELECT
                (SELECT count(*) FROM sys_user) AS total_users,
                (SELECT count(*) FROM sys_user WHERE enabled = TRUE) AS enabled_users,
                (SELECT count(*) FROM file_metadata WHERE status = 'READY') AS total_files,
                (
                    SELECT COALESCE(sum(size_bytes), 0)
                    FROM file_metadata
                    WHERE status = 'READY'
                ) AS total_file_size_bytes
            """)
    DashboardStatsRow selectStatistics();

    @Select("""
            WITH visible_datasets AS (
                SELECT dataset.id, dataset.duration_seconds
                FROM data_dataset dataset
                JOIN basic_project project ON project.id = dataset.project_id
                WHERE dataset.deleted = FALSE
                  AND
            """ + ACCESSIBLE_PROJECT + """
            ),
            dataset_summary AS (
                SELECT
                    count(*) AS dataset_count,
                    COALESCE(sum(duration_seconds), 0) AS dataset_duration_seconds
                FROM visible_datasets
            ),
            annotation_summary AS (
                SELECT
                    count(annotation.id) AS annotation_count,
                    COALESCE(sum(annotation.covered_duration_seconds), 0)
                        AS annotation_duration_seconds,
                    count(annotation.id) FILTER (WHERE annotation.reviewed = TRUE)
                        AS reviewed_annotation_count,
                    count(annotation.id) FILTER (
                        WHERE annotation.reviewed = TRUE AND annotation.is_qualified = TRUE
                    ) AS qualified_annotation_count,
                    count(annotation.id) FILTER (WHERE annotation.semantic_error = TRUE)
                        AS error_annotation_count,
                    count(annotation.id) FILTER (
                        WHERE annotation.semantic_error = TRUE
                          AND annotation.semantic_corrected = TRUE
                    ) AS corrected_annotation_count
                FROM data_annotation annotation
                JOIN visible_datasets dataset ON dataset.id = annotation.dataset_id
            )
            SELECT
                dataset_summary.dataset_count,
                dataset_summary.dataset_duration_seconds,
                annotation_summary.annotation_count,
                annotation_summary.annotation_duration_seconds,
                annotation_summary.reviewed_annotation_count,
                annotation_summary.qualified_annotation_count,
                annotation_summary.error_annotation_count,
                annotation_summary.corrected_annotation_count
            FROM dataset_summary
            CROSS JOIN annotation_summary
            """)
    BusinessSummaryRow selectBusinessSummary(
            @Param("userId") long userId,
            @Param("platformAdmin") boolean platformAdmin
    );

    @Select("""
            SELECT project.name, count(dataset.id) AS count
            FROM basic_project project
            JOIN data_dataset dataset
              ON dataset.project_id = project.id
             AND dataset.deleted = FALSE
            WHERE
            """ + ACCESSIBLE_PROJECT + """
            GROUP BY project.id, project.name
            ORDER BY count DESC, project.name, project.id
            LIMIT 12
            """)
    List<NamedCountRow> selectProjectDistribution(
            @Param("userId") long userId,
            @Param("platformAdmin") boolean platformAdmin
    );

    @Select("""
            SELECT task.status AS name, count(*) AS count
            FROM data_collection_task task
            JOIN basic_project project ON project.id = task.project_id
            WHERE task.deleted_at IS NULL
              AND
            """ + ACCESSIBLE_PROJECT + """
              AND (
                #{wideCollectionScope} = TRUE
                OR task.created_by = #{userId}
                OR EXISTS (
                    SELECT 1
                    FROM data_collection_task_assignee assignee
                    WHERE assignee.task_id = task.id
                      AND assignee.user_id = #{userId}
                )
              )
            GROUP BY task.status
            ORDER BY task.status
            """)
    List<NamedCountRow> selectCollectionDistribution(
            @Param("userId") long userId,
            @Param("platformAdmin") boolean platformAdmin,
            @Param("wideCollectionScope") boolean wideCollectionScope
    );

    @Select("""
            SELECT quality.name, count(*) AS count
            FROM (
                SELECT CASE
                    WHEN annotation.semantic_error = TRUE THEN 'ERROR'
                    WHEN annotation.is_valid = FALSE
                      OR annotation.invalid_collect = TRUE THEN 'INVALID'
                    ELSE 'VALID'
                END AS name
                FROM data_annotation annotation
                JOIN data_dataset dataset ON dataset.id = annotation.dataset_id
                JOIN basic_project project ON project.id = dataset.project_id
                WHERE dataset.deleted = FALSE
                  AND
            """ + ACCESSIBLE_PROJECT + """
            ) quality
            GROUP BY quality.name
            ORDER BY quality.name
            """)
    List<NamedCountRow> selectAnnotationQualityDistribution(
            @Param("userId") long userId,
            @Param("platformAdmin") boolean platformAdmin
    );

    @Select("""
            WITH latest_execution AS (
                SELECT DISTINCT ON (execution.dataset_id)
                    execution.dataset_id,
                    COALESCE(execution.override_pass, execution.passed) AS passed
                FROM data_qc_execution execution
                ORDER BY execution.dataset_id, execution.created_at DESC, execution.id DESC
            )
            SELECT result.name, count(*) AS count
            FROM (
                SELECT CASE
                    WHEN latest.passed = TRUE THEN 'PASSED'
                    WHEN latest.passed = FALSE THEN 'FAILED'
                    ELSE 'UNCHECKED'
                END AS name
                FROM data_dataset dataset
                JOIN basic_project project ON project.id = dataset.project_id
                LEFT JOIN latest_execution latest ON latest.dataset_id = dataset.id
                WHERE dataset.deleted = FALSE
                  AND
            """ + ACCESSIBLE_PROJECT + """
            ) result
            GROUP BY result.name
            ORDER BY result.name
            """)
    List<NamedCountRow> selectDataQualityDistribution(
            @Param("userId") long userId,
            @Param("platformAdmin") boolean platformAdmin
    );

    @Select("""
            WITH dates AS (
                SELECT generate_series(
                    CURRENT_DATE - INTERVAL '29 days',
                    CURRENT_DATE,
                    INTERVAL '1 day'
                )::date AS date
            ),
            daily AS (
                SELECT dataset.created_at::date AS date, count(*) AS count
                FROM data_dataset dataset
                JOIN basic_project project ON project.id = dataset.project_id
                WHERE dataset.deleted = FALSE
                  AND dataset.created_at >= CURRENT_DATE - INTERVAL '29 days'
                  AND
            """ + ACCESSIBLE_PROJECT + """
                GROUP BY dataset.created_at::date
            )
            SELECT dates.date, COALESCE(daily.count, 0) AS first_count,
                   0 AS second_count, 0 AS third_count
            FROM dates
            LEFT JOIN daily ON daily.date = dates.date
            ORDER BY dates.date
            """)
    List<TrendRow> selectDataGrowthTrend(
            @Param("userId") long userId,
            @Param("platformAdmin") boolean platformAdmin
    );

    @Select("""
            WITH dates AS (
                SELECT generate_series(
                    CURRENT_DATE - INTERVAL '29 days',
                    CURRENT_DATE,
                    INTERVAL '1 day'
                )::date AS date
            ),
            latest_execution AS (
                SELECT DISTINCT ON (execution.dataset_id)
                    execution.dataset_id,
                    COALESCE(execution.override_pass, execution.passed) AS passed
                FROM data_qc_execution execution
                ORDER BY execution.dataset_id, execution.created_at DESC, execution.id DESC
            ),
            daily AS (
                SELECT
                    dataset.created_at::date AS date,
                    count(*) FILTER (WHERE latest.passed = TRUE) AS first_count,
                    count(*) FILTER (WHERE latest.passed = FALSE) AS second_count,
                    count(*) FILTER (WHERE latest.passed IS NULL) AS third_count
                FROM data_dataset dataset
                JOIN basic_project project ON project.id = dataset.project_id
                LEFT JOIN latest_execution latest ON latest.dataset_id = dataset.id
                WHERE dataset.deleted = FALSE
                  AND dataset.created_at >= CURRENT_DATE - INTERVAL '29 days'
                  AND
            """ + ACCESSIBLE_PROJECT + """
                GROUP BY dataset.created_at::date
            )
            SELECT dates.date,
                   COALESCE(daily.first_count, 0) AS first_count,
                   COALESCE(daily.second_count, 0) AS second_count,
                   COALESCE(daily.third_count, 0) AS third_count
            FROM dates
            LEFT JOIN daily ON daily.date = dates.date
            ORDER BY dates.date
            """)
    List<TrendRow> selectDataQualityTrend(
            @Param("userId") long userId,
            @Param("platformAdmin") boolean platformAdmin
    );

    @Select("""
            WITH dates AS (
                SELECT generate_series(
                    CURRENT_DATE - INTERVAL '29 days',
                    CURRENT_DATE,
                    INTERVAL '1 day'
                )::date AS date
            ),
            daily AS (
                SELECT annotation.created_at::date AS date, count(*) AS count
                FROM data_annotation annotation
                JOIN data_dataset dataset ON dataset.id = annotation.dataset_id
                JOIN basic_project project ON project.id = dataset.project_id
                WHERE dataset.deleted = FALSE
                  AND annotation.created_at >= CURRENT_DATE - INTERVAL '29 days'
                  AND
            """ + ACCESSIBLE_PROJECT + """
                GROUP BY annotation.created_at::date
            )
            SELECT dates.date, COALESCE(daily.count, 0) AS first_count,
                   0 AS second_count, 0 AS third_count
            FROM dates
            LEFT JOIN daily ON daily.date = dates.date
            ORDER BY dates.date
            """)
    List<TrendRow> selectAnnotationGrowthTrend(
            @Param("userId") long userId,
            @Param("platformAdmin") boolean platformAdmin
    );

    @Select("""
            WITH dates AS (
                SELECT generate_series(
                    CURRENT_DATE - INTERVAL '29 days',
                    CURRENT_DATE,
                    INTERVAL '1 day'
                )::date AS date
            ),
            daily AS (
                SELECT
                    annotation.created_at::date AS date,
                    count(*) FILTER (
                        WHERE annotation.semantic_error = FALSE
                          AND annotation.is_valid = TRUE
                          AND annotation.invalid_collect = FALSE
                    ) AS first_count,
                    count(*) FILTER (WHERE annotation.semantic_error = TRUE) AS second_count,
                    count(*) FILTER (
                        WHERE annotation.semantic_error = FALSE
                          AND (annotation.is_valid = FALSE
                               OR annotation.invalid_collect = TRUE)
                    ) AS third_count
                FROM data_annotation annotation
                JOIN data_dataset dataset ON dataset.id = annotation.dataset_id
                JOIN basic_project project ON project.id = dataset.project_id
                WHERE dataset.deleted = FALSE
                  AND annotation.created_at >= CURRENT_DATE - INTERVAL '29 days'
                  AND
            """ + ACCESSIBLE_PROJECT + """
                GROUP BY annotation.created_at::date
            )
            SELECT dates.date,
                   COALESCE(daily.first_count, 0) AS first_count,
                   COALESCE(daily.second_count, 0) AS second_count,
                   COALESCE(daily.third_count, 0) AS third_count
            FROM dates
            LEFT JOIN daily ON daily.date = dates.date
            ORDER BY dates.date
            """)
    List<TrendRow> selectAnnotationQualityTrend(
            @Param("userId") long userId,
            @Param("platformAdmin") boolean platformAdmin
    );
}

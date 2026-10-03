package com.dayan.platform.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dayan.platform.model.DataExportTask;
import com.dayan.platform.repository.query.DataExportRows.AnnotationRow;
import com.dayan.platform.repository.query.DataExportRows.DatasetRow;
import com.dayan.platform.repository.query.DataExportRows.QuotaRow;
import com.dayan.platform.repository.query.DataExportRows.TaskRow;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface DataExportMapper extends BaseMapper<DataExportTask> {

    String ACCESS = """
            AND (
              #{admin} = TRUE
              OR d.uploader_id = #{userId}
              OR d.project_id IN (
                SELECT pm.project_id FROM basic_project_member pm
                WHERE pm.user_id = #{userId}
              )
              OR d.project_id IN (
                SELECT p.id FROM basic_project p
                WHERE p.access_level = 'PUBLIC' AND p.status = 'ACTIVE'
              )
            )
            """;

    String COMPLETED = """
            AND (
              d.annotation_status = 'COMPLETED'
              OR EXISTS (
                SELECT 1
                FROM data_annotation_task_dataset td
                JOIN data_annotation_task task ON task.id = td.task_id
                WHERE td.dataset_id = d.id
                  AND td.unassigned_at IS NULL
                  AND task.deleted_at IS NULL
                  AND task.status = 'SUBMITTED'
              )
            )
            """;

    String QUALITY_GATE = """
            AND NOT EXISTS (
              SELECT 1
              FROM data_qc_execution qc
              WHERE qc.dataset_id = d.id
                AND qc.status IN ('PASSED', 'FAILED')
                AND qc.id = (
                  SELECT latest.id
                  FROM data_qc_execution latest
                  WHERE latest.dataset_id = qc.dataset_id
                    AND latest.rule_id = qc.rule_id
                  ORDER BY latest.created_at DESC, latest.id DESC
                  LIMIT 1
                )
                AND COALESCE(qc.override_pass, qc.passed) = FALSE
            )
            """;

    String DATASET_COLUMNS = """
            SELECT d.id, d.name, d.data_type, d.size_bytes, d.duration_seconds,
                   d.project_id, p.name AS project_name,
                   collector.display_name AS collector_name, d.created_at,
                   f.object_key, f.original_name, f.content_type
            FROM data_dataset d
            JOIN file_metadata f ON f.id = d.file_id AND f.status = 'READY'
            LEFT JOIN basic_project p ON p.id = d.project_id
            LEFT JOIN sys_user collector ON collector.id = d.collector_id
            WHERE d.deleted = FALSE
            """;

    @Select("""
            <script>
            """ + DATASET_COLUMNS + ACCESS + COMPLETED + QUALITY_GATE + """
            <if test="projectId != null">AND d.project_id = #{projectId}</if>
            <if test="collectorId != null">AND d.collector_id = #{collectorId}</if>
            <if test="from != null">AND d.created_at &gt;= #{from}</if>
            <if test="to != null">AND d.created_at &lt;= #{to}</if>
            <if test="keyword != null and keyword != ''">
              AND (d.name ILIKE CONCAT('%', #{keyword}, '%')
                   OR p.name ILIKE CONCAT('%', #{keyword}, '%'))
            </if>
            ORDER BY d.created_at DESC, d.id DESC
            LIMIT 200
            </script>
            """)
    List<DatasetRow> selectOptions(
            @Param("userId") long userId,
            @Param("admin") boolean admin,
            @Param("projectId") Long projectId,
            @Param("collectorId") Long collectorId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            @Param("keyword") String keyword
    );

    @Select("""
            <script>
            """ + DATASET_COLUMNS + ACCESS + COMPLETED + QUALITY_GATE + """
            AND d.id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            ORDER BY array_position(
              ARRAY[
                <foreach collection="ids" item="id" separator=",">#{id}::bigint</foreach>
              ], d.id
            )
            </script>
            """)
    List<DatasetRow> selectExportable(
            @Param("ids") List<Long> ids,
            @Param("userId") long userId,
            @Param("admin") boolean admin
    );

    @Insert("""
            <script>
            INSERT INTO data_export_task_dataset (task_id, dataset_id, sequence_no)
            VALUES
            <foreach collection="ids" item="id" index="index" separator=",">
              (#{taskId}, #{id}, #{index} + 1)
            </foreach>
            </script>
            """)
    int insertTaskDatasets(@Param("taskId") long taskId, @Param("ids") List<Long> ids);

    @Select("""
            """ + DATASET_COLUMNS + """
            AND d.id IN (
              SELECT dataset_id FROM data_export_task_dataset WHERE task_id = #{taskId}
            )
            ORDER BY (
              SELECT sequence_no FROM data_export_task_dataset
              WHERE task_id = #{taskId} AND dataset_id = d.id
            )
            """)
    List<DatasetRow> selectTaskDatasets(@Param("taskId") long taskId);

    @Select("""
            SELECT a.id, a.dataset_id, a.task_id, a.annotator_id,
                   u.display_name AS annotator_name, a.content_text,
                   a.covered_duration_seconds, a.is_valid, a.is_qualified,
                   a.reviewed, a.created_at
            FROM data_annotation a
            LEFT JOIN sys_user u ON u.id = a.annotator_id
            WHERE a.dataset_id IN (
              SELECT dataset_id FROM data_export_task_dataset WHERE task_id = #{taskId}
            )
            ORDER BY a.dataset_id, a.created_at, a.id
            """)
    List<AnnotationRow> selectTaskAnnotations(@Param("taskId") long taskId);

    @Select("""
            SELECT task.*, u.display_name AS creator_name
            FROM data_export_task task
            JOIN sys_user u ON u.id = task.creator_id
            WHERE task.id = #{id}
              AND (#{admin} = TRUE OR task.creator_id = #{userId})
            """)
    TaskRow selectTask(
            @Param("id") long id,
            @Param("userId") long userId,
            @Param("admin") boolean admin
    );

    @Select("""
            SELECT task.*, u.display_name AS creator_name
            FROM data_export_task task
            JOIN sys_user u ON u.id = task.creator_id
            WHERE task.id = #{id}
            """)
    TaskRow selectTaskForWorker(@Param("id") long id);

    @Select("""
            <script>
            SELECT task.*, u.display_name AS creator_name
            FROM data_export_task task
            JOIN sys_user u ON u.id = task.creator_id
            WHERE (#{admin} = TRUE OR task.creator_id = #{userId})
            <if test="format != null and format != ''">AND task.format = #{format}</if>
            <if test="status != null and status != ''">AND task.status = #{status}</if>
            <if test="keyword != null and keyword != ''">
              AND (task.name ILIKE CONCAT('%', #{keyword}, '%')
                   OR task.file_name ILIKE CONCAT('%', #{keyword}, '%'))
            </if>
            ORDER BY task.created_at DESC, task.id DESC
            LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<TaskRow> selectTaskPage(
            @Param("userId") long userId,
            @Param("admin") boolean admin,
            @Param("format") String format,
            @Param("status") String status,
            @Param("keyword") String keyword,
            @Param("size") int size,
            @Param("offset") long offset
    );

    @Select("""
            <script>
            SELECT count(*)
            FROM data_export_task task
            WHERE (#{admin} = TRUE OR task.creator_id = #{userId})
            <if test="format != null and format != ''">AND task.format = #{format}</if>
            <if test="status != null and status != ''">AND task.status = #{status}</if>
            <if test="keyword != null and keyword != ''">
              AND (task.name ILIKE CONCAT('%', #{keyword}, '%')
                   OR task.file_name ILIKE CONCAT('%', #{keyword}, '%'))
            </if>
            </script>
            """)
    long countTasks(
            @Param("userId") long userId,
            @Param("admin") boolean admin,
            @Param("format") String format,
            @Param("status") String status,
            @Param("keyword") String keyword
    );

    @Select("""
            SELECT COALESCE((
                     SELECT quota_limit FROM data_export_quota WHERE user_id = #{userId}
                   ), 1000) AS quota_limit,
                   COALESCE((
                     SELECT sum(dataset_count) FROM data_export_task
                     WHERE creator_id = #{userId}
                       AND status != 'FAILED'
                       AND created_at >= date_trunc('month', CURRENT_TIMESTAMP)
                   ), 0) AS used
            """)
    QuotaRow selectQuota(@Param("userId") long userId);

    @Insert("""
            INSERT INTO data_export_quota (user_id, quota_limit)
            VALUES (#{userId}, 1000)
            ON CONFLICT (user_id) DO NOTHING
            """)
    int ensureQuota(@Param("userId") long userId);

    @Select("""
            SELECT q.quota_limit,
                   COALESCE((
                     SELECT sum(dataset_count) FROM data_export_task
                     WHERE creator_id = #{userId}
                       AND status != 'FAILED'
                       AND created_at >= date_trunc('month', CURRENT_TIMESTAMP)
                   ), 0) AS used
            FROM data_export_quota q
            WHERE q.user_id = #{userId}
            FOR UPDATE
            """)
    QuotaRow selectQuotaForUpdate(@Param("userId") long userId);

    @Select("""
            SELECT u.id AS user_id, u.username, u.display_name,
                   COALESCE(q.quota_limit, 1000) AS quota_limit,
                   COALESCE(sum(task.dataset_count)
                     FILTER (WHERE task.status != 'FAILED'
                       AND task.created_at >= date_trunc('month', CURRENT_TIMESTAMP)), 0) AS used
            FROM sys_user u
            LEFT JOIN data_export_quota q ON q.user_id = u.id
            LEFT JOIN data_export_task task ON task.creator_id = u.id
            GROUP BY u.id, u.username, u.display_name, q.quota_limit
            ORDER BY u.display_name, u.id
            """)
    List<QuotaRow> selectQuotas();

    @Insert("""
            INSERT INTO data_export_quota (user_id, quota_limit, updated_by)
            VALUES (#{userId}, #{quotaLimit}, #{updatedBy})
            ON CONFLICT (user_id) DO UPDATE
            SET quota_limit = EXCLUDED.quota_limit,
                updated_by = EXCLUDED.updated_by,
                updated_at = CURRENT_TIMESTAMP
            """)
    int upsertQuota(
            @Param("userId") long userId,
            @Param("quotaLimit") int quotaLimit,
            @Param("updatedBy") long updatedBy
    );

    @Update("""
            UPDATE data_export_task
            SET status = 'PROCESSING', progress = 1, started_at = CURRENT_TIMESTAMP,
                error_message = NULL, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND status = 'PENDING'
            """)
    int claim(@Param("id") long id);

    @Select("""
            SELECT id FROM data_export_task
            WHERE status = 'PENDING'
            ORDER BY created_at, id
            LIMIT 5
            """)
    List<Long> selectPendingIds();

    @Update("""
            UPDATE data_export_task
            SET processed_count = #{processed}, progress = #{progress},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND status = 'PROCESSING'
            """)
    int updateProgress(
            @Param("id") long id,
            @Param("processed") int processed,
            @Param("progress") int progress
    );

    @Update("""
            UPDATE data_export_task
            SET status = 'COMPLETED', progress = 100, processed_count = dataset_count,
                file_name = #{fileName}, object_key = #{objectKey},
                content_type = #{contentType}, file_size = #{fileSize},
                completed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND status = 'PROCESSING'
            """)
    int complete(
            @Param("id") long id,
            @Param("fileName") String fileName,
            @Param("objectKey") String objectKey,
            @Param("contentType") String contentType,
            @Param("fileSize") long fileSize
    );

    @Update("""
            UPDATE data_export_task
            SET status = 'FAILED', error_message = #{message},
                completed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND status = 'PROCESSING'
            """)
    int fail(@Param("id") long id, @Param("message") String message);
}

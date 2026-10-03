package com.dayan.platform.repository.mapper;

import com.dayan.platform.repository.query.MonitoringRows.AccessLogRow;
import com.dayan.platform.repository.query.MonitoringRows.ExportTaskRow;
import com.dayan.platform.repository.query.MonitoringRows.LoginLogRow;
import com.dayan.platform.repository.query.MonitoringRows.MetricSampleRow;
import com.dayan.platform.repository.query.MonitoringRows.OnlineUserRow;
import com.dayan.platform.repository.query.MonitoringRows.QueueCountsRow;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface MonitoringMapper {

    @Select("SELECT 1")
    int pingDatabase();

    @Select("SELECT count(*) FROM data_export_task WHERE status = 'PENDING'")
    int countQueueBacklog();

    @Insert("""
            INSERT INTO monitor_metric_sample (
              database_latency_ms, redis_latency_ms, queue_backlog,
              cpu_usage_percent, memory_usage_percent, collected_at
            ) VALUES (
              #{databaseLatencyMs}, #{redisLatencyMs}, #{queueBacklog},
              #{cpuUsagePercent}, #{memoryUsagePercent}, #{collectedAt}
            )
            """)
    int insertMetric(
            @Param("databaseLatencyMs") long databaseLatencyMs,
            @Param("redisLatencyMs") Long redisLatencyMs,
            @Param("queueBacklog") int queueBacklog,
            @Param("cpuUsagePercent") BigDecimal cpuUsagePercent,
            @Param("memoryUsagePercent") BigDecimal memoryUsagePercent,
            @Param("collectedAt") OffsetDateTime collectedAt
    );

    @Select("""
            SELECT *
            FROM monitor_metric_sample
            WHERE collected_at >= #{since}
            ORDER BY collected_at
            """)
    List<MetricSampleRow> selectMetricsSince(@Param("since") OffsetDateTime since);

    @Delete("DELETE FROM monitor_metric_sample WHERE collected_at < #{before}")
    int deleteOldMetrics(@Param("before") OffsetDateTime before);

    @Insert("""
            INSERT INTO request_access_log (
              user_id, username, method, request_path, status_code, duration_ms,
              ip_address, user_agent, request_id, occurred_at
            ) VALUES (
              #{userId}, #{username}, #{method}, #{requestPath}, #{statusCode}, #{durationMs},
              #{ipAddress}, #{userAgent}, #{requestId}, #{occurredAt}
            )
            """)
    int insertAccessLog(
            @Param("userId") Long userId,
            @Param("username") String username,
            @Param("method") String method,
            @Param("requestPath") String requestPath,
            @Param("statusCode") int statusCode,
            @Param("durationMs") long durationMs,
            @Param("ipAddress") String ipAddress,
            @Param("userAgent") String userAgent,
            @Param("requestId") String requestId,
            @Param("occurredAt") OffsetDateTime occurredAt
    );

    @Select("""
            <script>
            SELECT *
            FROM request_access_log
            <where>
              <if test="path != null and path != ''">
                AND request_path ILIKE CONCAT('%', #{path}, '%')
              </if>
              <if test="username != null and username != ''">
                AND username ILIKE CONCAT('%', #{username}, '%')
              </if>
              <if test="statusCode != null">AND status_code = #{statusCode}</if>
              <if test="startTime != null">AND occurred_at &gt;= #{startTime}</if>
              <if test="endTime != null">AND occurred_at &lt;= #{endTime}</if>
            </where>
            ORDER BY occurred_at DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<AccessLogRow> selectAccessLogs(
            @Param("path") String path,
            @Param("username") String username,
            @Param("statusCode") Integer statusCode,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("limit") int limit,
            @Param("offset") long offset
    );

    @Select("""
            <script>
            SELECT count(*)
            FROM request_access_log
            <where>
              <if test="path != null and path != ''">
                AND request_path ILIKE CONCAT('%', #{path}, '%')
              </if>
              <if test="username != null and username != ''">
                AND username ILIKE CONCAT('%', #{username}, '%')
              </if>
              <if test="statusCode != null">AND status_code = #{statusCode}</if>
              <if test="startTime != null">AND occurred_at &gt;= #{startTime}</if>
              <if test="endTime != null">AND occurred_at &lt;= #{endTime}</if>
            </where>
            </script>
            """)
    long countAccessLogs(
            @Param("path") String path,
            @Param("username") String username,
            @Param("statusCode") Integer statusCode,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    @Select("""
            SELECT s.id AS session_id, u.id AS user_id, u.username, u.display_name,
                   COALESCE(string_agg(DISTINCT r.name, ', '), '') AS roles,
                   GREATEST(COALESCE(s.last_used_at, s.created_at),
                     COALESCE(latest.occurred_at, s.created_at)) AS active_at,
                   s.created_at AS session_started_at,
                   latest.request_path AS current_path,
                   COALESCE(latest.ip_address, s.ip_address) AS ip_address
            FROM auth_session s
            JOIN sys_user u ON u.id = s.user_id
            LEFT JOIN sys_user_role ur ON ur.user_id = u.id
            LEFT JOIN sys_role r ON r.id = ur.role_id
            LEFT JOIN LATERAL (
              SELECT request_path, ip_address, occurred_at
              FROM request_access_log access
              WHERE access.user_id = s.user_id
              ORDER BY occurred_at DESC
              LIMIT 1
            ) latest ON TRUE
            WHERE s.revoked_at IS NULL
              AND s.expires_at > CURRENT_TIMESTAMP
              AND GREATEST(COALESCE(s.last_used_at, s.created_at),
                    COALESCE(latest.occurred_at, s.created_at))
                  >= CURRENT_TIMESTAMP - INTERVAL '15 minutes'
            GROUP BY s.id, u.id, u.username, u.display_name,
                     s.last_used_at, s.created_at, latest.occurred_at,
                     latest.request_path, latest.ip_address, s.ip_address
            ORDER BY active_at DESC
            """)
    List<OnlineUserRow> selectOnlineUsers();

    @Select("""
            SELECT count(DISTINCT user_id)
            FROM request_access_log
            WHERE user_id IS NOT NULL
              AND occurred_at >= date_trunc('day', CURRENT_TIMESTAMP)
            """)
    long countTodayActiveUsers();

    @Select("""
            <script>
            SELECT id, COALESCE(operator_name, target_id) AS username, result,
                   error_summary, ip_address, occurred_at
            FROM operation_log
            WHERE module = 'AUTH' AND action = 'LOGIN'
            <if test="username != null and username != ''">
              AND COALESCE(operator_name, target_id) ILIKE CONCAT('%', #{username}, '%')
            </if>
            <if test="ipAddress != null and ipAddress != ''">
              AND ip_address ILIKE CONCAT('%', #{ipAddress}, '%')
            </if>
            <if test="result != null and result != ''">AND result = #{result}</if>
            <if test="startTime != null">AND occurred_at &gt;= #{startTime}</if>
            <if test="endTime != null">AND occurred_at &lt;= #{endTime}</if>
            ORDER BY occurred_at DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<LoginLogRow> selectLoginLogs(
            @Param("username") String username,
            @Param("ipAddress") String ipAddress,
            @Param("result") String result,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("limit") int limit,
            @Param("offset") long offset
    );

    @Select("""
            <script>
            SELECT count(*)
            FROM operation_log
            WHERE module = 'AUTH' AND action = 'LOGIN'
            <if test="username != null and username != ''">
              AND COALESCE(operator_name, target_id) ILIKE CONCAT('%', #{username}, '%')
            </if>
            <if test="ipAddress != null and ipAddress != ''">
              AND ip_address ILIKE CONCAT('%', #{ipAddress}, '%')
            </if>
            <if test="result != null and result != ''">AND result = #{result}</if>
            <if test="startTime != null">AND occurred_at &gt;= #{startTime}</if>
            <if test="endTime != null">AND occurred_at &lt;= #{endTime}</if>
            </script>
            """)
    long countLoginLogs(
            @Param("username") String username,
            @Param("ipAddress") String ipAddress,
            @Param("result") String result,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    @Select("""
            <script>
            SELECT task.*, u.display_name AS creator_name
            FROM data_export_task task
            JOIN sys_user u ON u.id = task.creator_id
            <where>
              <if test="keyword != null and keyword != ''">
                AND (task.name ILIKE CONCAT('%', #{keyword}, '%')
                     OR task.file_name ILIKE CONCAT('%', #{keyword}, '%'))
              </if>
              <if test="username != null and username != ''">
                AND (u.username ILIKE CONCAT('%', #{username}, '%')
                     OR u.display_name ILIKE CONCAT('%', #{username}, '%'))
              </if>
              <if test="format != null and format != ''">AND task.format = #{format}</if>
              <if test="status != null and status != ''">AND task.status = #{status}</if>
              <if test="startTime != null">AND task.created_at &gt;= #{startTime}</if>
              <if test="endTime != null">AND task.created_at &lt;= #{endTime}</if>
            </where>
            ORDER BY task.created_at DESC, task.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<ExportTaskRow> selectExportTasks(
            @Param("keyword") String keyword,
            @Param("username") String username,
            @Param("format") String format,
            @Param("status") String status,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("limit") int limit,
            @Param("offset") long offset
    );

    @Select("""
            <script>
            SELECT count(*)
            FROM data_export_task task
            JOIN sys_user u ON u.id = task.creator_id
            <where>
              <if test="keyword != null and keyword != ''">
                AND (task.name ILIKE CONCAT('%', #{keyword}, '%')
                     OR task.file_name ILIKE CONCAT('%', #{keyword}, '%'))
              </if>
              <if test="username != null and username != ''">
                AND (u.username ILIKE CONCAT('%', #{username}, '%')
                     OR u.display_name ILIKE CONCAT('%', #{username}, '%'))
              </if>
              <if test="format != null and format != ''">AND task.format = #{format}</if>
              <if test="status != null and status != ''">AND task.status = #{status}</if>
              <if test="startTime != null">AND task.created_at &gt;= #{startTime}</if>
              <if test="endTime != null">AND task.created_at &lt;= #{endTime}</if>
            </where>
            </script>
            """)
    long countExportTasks(
            @Param("keyword") String keyword,
            @Param("username") String username,
            @Param("format") String format,
            @Param("status") String status,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    @Select("""
            SELECT count(*) FILTER (WHERE status = 'PENDING') AS pending,
                   count(*) FILTER (WHERE status = 'PROCESSING') AS processing,
                   count(*) FILTER (WHERE status = 'COMPLETED') AS completed,
                   count(*) FILTER (WHERE status = 'FAILED') AS failed,
                   count(*) FILTER (WHERE status = 'CANCELED') AS canceled
            FROM data_export_task
            """)
    QueueCountsRow selectQueueCounts();

    @Select("SELECT paused FROM monitor_queue_state WHERE queue_name = 'EXPORT'")
    boolean isExportQueuePaused();

    @Update("""
            UPDATE monitor_queue_state
            SET paused = #{paused}, updated_by = #{userId}, updated_at = CURRENT_TIMESTAMP
            WHERE queue_name = 'EXPORT'
            """)
    int setExportQueuePaused(@Param("paused") boolean paused, @Param("userId") long userId);

    @Update("""
            UPDATE data_export_task
            SET status = 'PENDING', progress = 0, processed_count = 0,
                started_at = NULL, completed_at = NULL, error_message = NULL,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND status = 'FAILED'
            """)
    int retryExportTask(@Param("id") long id);

    @Update("""
            UPDATE data_export_task
            SET status = 'PENDING', progress = 0, processed_count = 0,
                started_at = NULL, completed_at = NULL, error_message = NULL,
                updated_at = CURRENT_TIMESTAMP
            WHERE status = 'FAILED'
            """)
    int retryAllFailedExportTasks();

    @Update("""
            UPDATE data_export_task
            SET status = 'CANCELED', completed_at = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND status = 'PENDING'
            """)
    int cancelExportTask(@Param("id") long id);

    @Delete("""
            DELETE FROM data_export_task
            WHERE id = #{id} AND status IN ('COMPLETED', 'FAILED', 'CANCELED')
            """)
    int deleteExportTask(@Param("id") long id);

    @Delete("DELETE FROM data_export_task WHERE status = 'PENDING'")
    int clearPendingExportTasks();

    @Delete("""
            DELETE FROM data_export_task
            WHERE status IN ('COMPLETED', 'FAILED', 'CANCELED')
              AND COALESCE(completed_at, updated_at) < CURRENT_TIMESTAMP - INTERVAL '24 hours'
            """)
    int cleanExportHistory();
}

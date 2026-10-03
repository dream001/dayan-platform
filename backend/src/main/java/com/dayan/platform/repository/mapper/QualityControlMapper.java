package com.dayan.platform.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dayan.platform.model.QualityExecution;
import com.dayan.platform.model.QualityRule;
import com.dayan.platform.repository.query.QualityControlRows.DatasetRow;
import com.dayan.platform.repository.query.QualityControlRows.ExecutionRow;
import com.dayan.platform.repository.query.QualityControlRows.OverviewRow;
import com.dayan.platform.repository.query.QualityControlRows.RuleRow;
import com.dayan.platform.vo.QualityControlViews.ProjectOption;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface QualityControlMapper extends BaseMapper<QualityRule> {

    String RULE_ACCESS = """
            (#{admin} = TRUE
             OR r.scope = 'GLOBAL'
             OR EXISTS (
                 SELECT 1 FROM basic_project_member pm
                 WHERE pm.project_id = r.project_id
                   AND pm.user_id = #{userId}
                   AND (pm.valid_from IS NULL OR CURRENT_TIMESTAMP >= pm.valid_from)
                   AND (pm.valid_until IS NULL OR pm.valid_until > CURRENT_TIMESTAMP)
             ))
            """;

    String EXECUTION_SELECT = """
            SELECT e.*, r.name AS rule_name, r.scope AS rule_scope,
                   d.name AS dataset_name, d.data_type,
                   d.project_id, p.name AS project_name,
                   override_user.display_name AS override_by_name,
                   COALESCE(e.override_pass, e.passed) AS effective_pass
            FROM data_qc_execution e
            JOIN data_qc_rule r ON r.id = e.rule_id
            JOIN data_dataset d ON d.id = e.dataset_id
            LEFT JOIN basic_project p ON p.id = d.project_id
            LEFT JOIN sys_user override_user ON override_user.id = e.override_by
            """;

    @Select("""
            <script>
            SELECT r.*, p.name AS project_name, u.display_name AS creator_name
            FROM data_qc_rule r
            LEFT JOIN basic_project p ON p.id = r.project_id
            JOIN sys_user u ON u.id = r.creator_id
            WHERE """ + RULE_ACCESS + """
              <if test="projectId != null">
                AND (r.scope = 'GLOBAL' OR r.project_id = #{projectId})
              </if>
              <if test="enabled != null">AND r.enabled = #{enabled}</if>
              <if test="keyword != null and keyword != ''">
                AND (r.name ILIKE CONCAT('%', #{keyword}, '%')
                     OR r.description ILIKE CONCAT('%', #{keyword}, '%'))
              </if>
            ORDER BY r.priority, r.id
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<RuleRow> selectRulePage(
            @Param("userId") long userId,
            @Param("admin") boolean admin,
            @Param("projectId") Long projectId,
            @Param("enabled") Boolean enabled,
            @Param("keyword") String keyword,
            @Param("offset") long offset,
            @Param("limit") int limit
    );

    @Select("""
            <script>
            SELECT count(*)
            FROM data_qc_rule r
            WHERE """ + RULE_ACCESS + """
              <if test="projectId != null">
                AND (r.scope = 'GLOBAL' OR r.project_id = #{projectId})
              </if>
              <if test="enabled != null">AND r.enabled = #{enabled}</if>
              <if test="keyword != null and keyword != ''">
                AND (r.name ILIKE CONCAT('%', #{keyword}, '%')
                     OR r.description ILIKE CONCAT('%', #{keyword}, '%'))
              </if>
            </script>
            """)
    long countRules(
            @Param("userId") long userId,
            @Param("admin") boolean admin,
            @Param("projectId") Long projectId,
            @Param("enabled") Boolean enabled,
            @Param("keyword") String keyword
    );

    @Select("""
            SELECT r.*, p.name AS project_name, u.display_name AS creator_name
            FROM data_qc_rule r
            LEFT JOIN basic_project p ON p.id = r.project_id
            JOIN sys_user u ON u.id = r.creator_id
            WHERE r.id = #{id}
            """)
    RuleRow selectRule(@Param("id") long id);

    @Select("""
            SELECT count(*)
            FROM basic_project_member
            WHERE project_id = #{projectId}
              AND user_id = #{userId}
              AND role IN ('PROJECT_ADMIN', 'PROJECT_MANAGER')
              AND (valid_from IS NULL OR CURRENT_TIMESTAMP >= valid_from)
              AND (valid_until IS NULL OR valid_until > CURRENT_TIMESTAMP)
            """)
    long countManagedProject(
            @Param("projectId") long projectId,
            @Param("userId") long userId
    );

    @Select("""
            SELECT p.id, p.name
            FROM basic_project p
            WHERE p.status != 'ARCHIVED'
              AND (#{admin} = TRUE OR EXISTS (
                  SELECT 1 FROM basic_project_member pm
                  WHERE pm.project_id = p.id
                    AND pm.user_id = #{userId}
                    AND (pm.valid_from IS NULL OR CURRENT_TIMESTAMP >= pm.valid_from)
                    AND (pm.valid_until IS NULL OR pm.valid_until > CURRENT_TIMESTAMP)
              ))
            ORDER BY p.name, p.id
            """)
    List<ProjectOption> selectProjects(
            @Param("userId") long userId,
            @Param("admin") boolean admin
    );

    @Select("""
            SELECT d.id, d.name, d.data_type, p.name AS project_name
            FROM data_dataset d
            LEFT JOIN basic_project p ON p.id = d.project_id
            WHERE d.deleted = FALSE
              AND (#{admin} = TRUE OR d.uploader_id = #{userId} OR EXISTS (
                  SELECT 1 FROM basic_project_member pm
                  WHERE pm.project_id = d.project_id AND pm.user_id = #{userId}
                    AND (pm.valid_from IS NULL OR CURRENT_TIMESTAMP >= pm.valid_from)
                    AND (pm.valid_until IS NULL OR pm.valid_until > CURRENT_TIMESTAMP)
              ))
            ORDER BY d.updated_at DESC, d.id DESC
            LIMIT 200
            """)
    List<DatasetRow> selectDatasets(
            @Param("userId") long userId,
            @Param("admin") boolean admin
    );

    @Select("""
            SELECT d.id, d.name, d.data_type, p.name AS project_name
            FROM data_dataset d
            LEFT JOIN basic_project p ON p.id = d.project_id
            WHERE d.id = #{datasetId}
              AND d.deleted = FALSE
              AND (#{admin} = TRUE OR d.uploader_id = #{userId} OR EXISTS (
                  SELECT 1 FROM basic_project_member pm
                  WHERE pm.project_id = d.project_id AND pm.user_id = #{userId}
                    AND (pm.valid_from IS NULL OR CURRENT_TIMESTAMP >= pm.valid_from)
                    AND (pm.valid_until IS NULL OR pm.valid_until > CURRENT_TIMESTAMP)
              ))
            """)
    DatasetRow selectAccessibleDataset(
            @Param("datasetId") long datasetId,
            @Param("userId") long userId,
            @Param("admin") boolean admin
    );

    @Select("""
            SELECT r.*, p.name AS project_name, u.display_name AS creator_name
            FROM data_qc_rule r
            JOIN data_dataset d ON d.id = #{datasetId}
            LEFT JOIN basic_project p ON p.id = r.project_id
            JOIN sys_user u ON u.id = r.creator_id
            WHERE r.enabled = TRUE
              AND (r.scope = 'GLOBAL' OR r.project_id = d.project_id)
              AND r.data_type = d.data_type
              AND d.name LIKE replace(replace(r.dataset_pattern, '*', '%'), '?', '_')
            ORDER BY r.priority, r.id
            """)
    List<RuleRow> selectMatchingRules(@Param("datasetId") long datasetId);

    @Insert("""
            INSERT INTO data_qc_execution
                (rule_id, dataset_id, status, progress, trigger_type, created_by)
            VALUES
                (#{ruleId}, #{datasetId}, #{status}, #{progress}, #{triggerType}, #{createdBy})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertExecution(QualityExecution execution);

    @Select("""
            <script>
            """ + EXECUTION_SELECT + """
            WHERE (#{admin} = TRUE OR d.uploader_id = #{userId} OR EXISTS (
                SELECT 1 FROM basic_project_member pm
                WHERE pm.project_id = d.project_id AND pm.user_id = #{userId}
                  AND (pm.valid_from IS NULL OR CURRENT_TIMESTAMP >= pm.valid_from)
                  AND (pm.valid_until IS NULL OR pm.valid_until > CURRENT_TIMESTAMP)
            ))
              <if test="projectId != null">AND d.project_id = #{projectId}</if>
              <if test="datasetId != null">AND e.dataset_id = #{datasetId}</if>
              <if test="ruleId != null">AND e.rule_id = #{ruleId}</if>
              <if test="status != null and status != ''">AND e.status = #{status}</if>
              <if test="effectivePass != null">
                AND COALESCE(e.override_pass, e.passed) = #{effectivePass}
              </if>
              <if test="overridden != null and overridden">AND e.override_pass IS NOT NULL</if>
              <if test="overridden != null and !overridden">AND e.override_pass IS NULL</if>
              <if test="keyword != null and keyword != ''">
                AND (d.name ILIKE CONCAT('%', #{keyword}, '%')
                     OR r.name ILIKE CONCAT('%', #{keyword}, '%'))
              </if>
            ORDER BY e.created_at DESC, e.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<ExecutionRow> selectExecutionPage(
            @Param("userId") long userId,
            @Param("admin") boolean admin,
            @Param("projectId") Long projectId,
            @Param("datasetId") Long datasetId,
            @Param("ruleId") Long ruleId,
            @Param("status") String status,
            @Param("effectivePass") Boolean effectivePass,
            @Param("overridden") Boolean overridden,
            @Param("keyword") String keyword,
            @Param("offset") long offset,
            @Param("limit") int limit
    );

    @Select("""
            <script>
            SELECT count(*)
            FROM data_qc_execution e
            JOIN data_qc_rule r ON r.id = e.rule_id
            JOIN data_dataset d ON d.id = e.dataset_id
            WHERE (#{admin} = TRUE OR d.uploader_id = #{userId} OR EXISTS (
                SELECT 1 FROM basic_project_member pm
                WHERE pm.project_id = d.project_id AND pm.user_id = #{userId}
                  AND (pm.valid_from IS NULL OR CURRENT_TIMESTAMP >= pm.valid_from)
                  AND (pm.valid_until IS NULL OR pm.valid_until > CURRENT_TIMESTAMP)
            ))
              <if test="projectId != null">AND d.project_id = #{projectId}</if>
              <if test="datasetId != null">AND e.dataset_id = #{datasetId}</if>
              <if test="ruleId != null">AND e.rule_id = #{ruleId}</if>
              <if test="status != null and status != ''">AND e.status = #{status}</if>
              <if test="effectivePass != null">
                AND COALESCE(e.override_pass, e.passed) = #{effectivePass}
              </if>
              <if test="overridden != null and overridden">AND e.override_pass IS NOT NULL</if>
              <if test="overridden != null and !overridden">AND e.override_pass IS NULL</if>
              <if test="keyword != null and keyword != ''">
                AND (d.name ILIKE CONCAT('%', #{keyword}, '%')
                     OR r.name ILIKE CONCAT('%', #{keyword}, '%'))
              </if>
            </script>
            """)
    long countExecutions(
            @Param("userId") long userId,
            @Param("admin") boolean admin,
            @Param("projectId") Long projectId,
            @Param("datasetId") Long datasetId,
            @Param("ruleId") Long ruleId,
            @Param("status") String status,
            @Param("effectivePass") Boolean effectivePass,
            @Param("overridden") Boolean overridden,
            @Param("keyword") String keyword
    );

    @Select("""
            """ + EXECUTION_SELECT + """
            WHERE e.id = #{id}
              AND (#{admin} = TRUE OR d.uploader_id = #{userId} OR EXISTS (
                  SELECT 1 FROM basic_project_member pm
                  WHERE pm.project_id = d.project_id AND pm.user_id = #{userId}
                    AND (pm.valid_from IS NULL OR CURRENT_TIMESTAMP >= pm.valid_from)
                    AND (pm.valid_until IS NULL OR pm.valid_until > CURRENT_TIMESTAMP)
              ))
            """)
    ExecutionRow selectExecution(
            @Param("id") long id,
            @Param("userId") long userId,
            @Param("admin") boolean admin
    );

    @Update("""
            UPDATE data_qc_execution
            SET status = #{status},
                progress = 100,
                passed = #{passed},
                report_json = #{reportJson},
                result_json = #{resultJson},
                error_message = NULL,
                started_at = COALESCE(started_at, #{completedAt}),
                completed_at = #{completedAt},
                updated_at = #{completedAt}
            WHERE id = #{id}
              AND status IN ('QUEUED', 'RUNNING')
            """)
    int completeExecution(
            @Param("id") long id,
            @Param("status") String status,
            @Param("passed") boolean passed,
            @Param("reportJson") String reportJson,
            @Param("resultJson") String resultJson,
            @Param("completedAt") OffsetDateTime completedAt
    );

    @Update("""
            UPDATE data_qc_execution
            SET override_pass = #{passed},
                override_reason = #{reason},
                override_by = #{userId},
                override_at = #{now},
                updated_at = #{now}
            WHERE id = #{id}
            """)
    int overrideExecution(
            @Param("id") long id,
            @Param("passed") boolean passed,
            @Param("reason") String reason,
            @Param("userId") long userId,
            @Param("now") OffsetDateTime now
    );

    @Update("""
            UPDATE data_qc_execution
            SET override_pass = NULL,
                override_reason = NULL,
                override_by = NULL,
                override_at = NULL,
                updated_at = #{now}
            WHERE id = #{id}
            """)
    int clearOverride(@Param("id") long id, @Param("now") OffsetDateTime now);

    @Select("""
            <script>
            WITH accessible_rules AS (
                SELECT r.id, r.enabled
                FROM data_qc_rule r
                WHERE """ + RULE_ACCESS + """
            ),
            accessible_executions AS (
                SELECT e.*
                FROM data_qc_execution e
                JOIN data_dataset d ON d.id = e.dataset_id
                WHERE (#{admin} = TRUE OR d.uploader_id = #{userId} OR EXISTS (
                    SELECT 1 FROM basic_project_member pm
                    WHERE pm.project_id = d.project_id AND pm.user_id = #{userId}
                      AND (pm.valid_from IS NULL OR CURRENT_TIMESTAMP >= pm.valid_from)
                      AND (pm.valid_until IS NULL OR pm.valid_until > CURRENT_TIMESTAMP)
                ))
            )
            SELECT
              (SELECT count(*) FROM accessible_rules) AS total_rules,
              (SELECT count(*) FROM accessible_rules WHERE enabled) AS enabled_rules,
              (SELECT count(*) FROM accessible_executions
               WHERE status IN ('QUEUED', 'RUNNING')) AS queued,
              (SELECT count(*) FROM accessible_executions
               WHERE COALESCE(override_pass, passed) = TRUE) AS passed,
              (SELECT count(*) FROM accessible_executions
               WHERE COALESCE(override_pass, passed) = FALSE) AS failed,
              (SELECT count(*) FROM accessible_executions
               WHERE override_pass IS NOT NULL) AS overridden
            </script>
            """)
    OverviewRow selectOverview(
            @Param("userId") long userId,
            @Param("admin") boolean admin
    );

    @Delete("DELETE FROM data_qc_rule WHERE id = #{id}")
    int deleteRule(@Param("id") long id);
}

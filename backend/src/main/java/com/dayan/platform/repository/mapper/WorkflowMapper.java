package com.dayan.platform.repository.mapper;

import com.dayan.platform.repository.query.WorkflowRows.ActionRuleRow;
import com.dayan.platform.repository.query.WorkflowRows.DatasetRow;
import com.dayan.platform.repository.query.WorkflowRows.DefinitionRow;
import com.dayan.platform.repository.query.WorkflowRows.MatchRuleRow;
import com.dayan.platform.repository.query.WorkflowRows.OverviewRow;
import com.dayan.platform.repository.query.WorkflowRows.RunRow;
import com.dayan.platform.vo.WorkflowViews.ProjectOption;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface WorkflowMapper {

    String PROJECT_ACCESS = """
            (#{admin} = TRUE OR project_id IS NULL OR EXISTS (
                SELECT 1 FROM basic_project_member pm
                WHERE pm.project_id = project_id
                  AND pm.user_id = #{userId}
                  AND (pm.valid_from IS NULL OR CURRENT_TIMESTAMP >= pm.valid_from)
                  AND (pm.valid_until IS NULL OR pm.valid_until > CURRENT_TIMESTAMP)
            ))
            """;

    @Select("""
            <script>
            SELECT r.id, r.name, r.description, r.scope, r.project_id, p.name AS project_name,
                   r.priority, r.enabled, r.logic_operator, r.conditions_json,
                   r.creator_id, r.updated_at
            FROM workflow_match_rule r
            LEFT JOIN basic_project p ON p.id = r.project_id
            WHERE r.deleted_at IS NULL
              AND (#{admin} = TRUE OR r.scope = 'GLOBAL' OR EXISTS (
                  SELECT 1 FROM basic_project_member pm
                  WHERE pm.project_id = r.project_id AND pm.user_id = #{userId}
                    AND (pm.valid_from IS NULL OR CURRENT_TIMESTAMP >= pm.valid_from)
                    AND (pm.valid_until IS NULL OR pm.valid_until > CURRENT_TIMESTAMP)
              ))
              <if test="projectId != null">AND (r.scope = 'GLOBAL' OR r.project_id = #{projectId})</if>
              <if test="enabled != null">AND r.enabled = #{enabled}</if>
              <if test="keyword != null and keyword != ''">
                AND (r.name ILIKE CONCAT('%', #{keyword}, '%')
                     OR r.description ILIKE CONCAT('%', #{keyword}, '%'))
              </if>
            ORDER BY r.priority, r.id
            LIMIT 200
            </script>
            """)
    List<MatchRuleRow> selectMatchRules(
            @Param("userId") long userId,
            @Param("admin") boolean admin,
            @Param("projectId") Long projectId,
            @Param("enabled") Boolean enabled,
            @Param("keyword") String keyword
    );

    @Select("""
            SELECT r.id, r.name, r.description, r.scope, r.project_id, p.name AS project_name,
                   r.priority, r.enabled, r.logic_operator, r.conditions_json,
                   r.creator_id, r.updated_at
            FROM workflow_match_rule r
            LEFT JOIN basic_project p ON p.id = r.project_id
            WHERE r.id = #{id} AND r.deleted_at IS NULL
            """)
    MatchRuleRow selectMatchRule(@Param("id") long id);

    @Select("""
            <script>
            SELECT r.id, r.name, r.description, r.scope, r.project_id, p.name AS project_name,
                   r.enabled, r.steps_json, r.creator_id, r.updated_at
            FROM workflow_action_rule r
            LEFT JOIN basic_project p ON p.id = r.project_id
            WHERE r.deleted_at IS NULL
              AND (#{admin} = TRUE OR r.scope = 'GLOBAL' OR EXISTS (
                  SELECT 1 FROM basic_project_member pm
                  WHERE pm.project_id = r.project_id AND pm.user_id = #{userId}
                    AND (pm.valid_from IS NULL OR CURRENT_TIMESTAMP >= pm.valid_from)
                    AND (pm.valid_until IS NULL OR pm.valid_until > CURRENT_TIMESTAMP)
              ))
              <if test="projectId != null">AND (r.scope = 'GLOBAL' OR r.project_id = #{projectId})</if>
              <if test="enabled != null">AND r.enabled = #{enabled}</if>
              <if test="keyword != null and keyword != ''">
                AND (r.name ILIKE CONCAT('%', #{keyword}, '%')
                     OR r.description ILIKE CONCAT('%', #{keyword}, '%'))
              </if>
            ORDER BY r.updated_at DESC, r.id DESC
            LIMIT 200
            </script>
            """)
    List<ActionRuleRow> selectActionRules(
            @Param("userId") long userId,
            @Param("admin") boolean admin,
            @Param("projectId") Long projectId,
            @Param("enabled") Boolean enabled,
            @Param("keyword") String keyword
    );

    @Select("""
            SELECT r.id, r.name, r.description, r.scope, r.project_id, p.name AS project_name,
                   r.enabled, r.steps_json, r.creator_id, r.updated_at
            FROM workflow_action_rule r
            LEFT JOIN basic_project p ON p.id = r.project_id
            WHERE r.id = #{id} AND r.deleted_at IS NULL
            """)
    ActionRuleRow selectActionRule(@Param("id") long id);

    @Select("""
            <script>
            SELECT w.id, w.name, w.description, w.scope, w.project_id, p.name AS project_name,
                   w.priority, w.enabled, w.match_rule_id, m.name AS match_rule_name,
                   w.action_rule_id, a.name AS action_rule_name, a.steps_json,
                   w.creator_id, w.updated_at
            FROM workflow_definition w
            LEFT JOIN basic_project p ON p.id = w.project_id
            JOIN workflow_match_rule m ON m.id = w.match_rule_id
            JOIN workflow_action_rule a ON a.id = w.action_rule_id
            WHERE w.deleted_at IS NULL
              AND (#{admin} = TRUE OR w.scope = 'GLOBAL' OR EXISTS (
                  SELECT 1 FROM basic_project_member pm
                  WHERE pm.project_id = w.project_id AND pm.user_id = #{userId}
                    AND (pm.valid_from IS NULL OR CURRENT_TIMESTAMP >= pm.valid_from)
                    AND (pm.valid_until IS NULL OR pm.valid_until > CURRENT_TIMESTAMP)
              ))
              <if test="projectId != null">AND (w.scope = 'GLOBAL' OR w.project_id = #{projectId})</if>
              <if test="enabled != null">AND w.enabled = #{enabled}</if>
              <if test="keyword != null and keyword != ''">
                AND (w.name ILIKE CONCAT('%', #{keyword}, '%')
                     OR w.description ILIKE CONCAT('%', #{keyword}, '%'))
              </if>
            ORDER BY w.priority, w.id
            LIMIT 200
            </script>
            """)
    List<DefinitionRow> selectDefinitions(
            @Param("userId") long userId,
            @Param("admin") boolean admin,
            @Param("projectId") Long projectId,
            @Param("enabled") Boolean enabled,
            @Param("keyword") String keyword
    );

    @Select("""
            SELECT w.id, w.name, w.description, w.scope, w.project_id, p.name AS project_name,
                   w.priority, w.enabled, w.match_rule_id, m.name AS match_rule_name,
                   w.action_rule_id, a.name AS action_rule_name, a.steps_json,
                   w.creator_id, w.updated_at
            FROM workflow_definition w
            LEFT JOIN basic_project p ON p.id = w.project_id
            JOIN workflow_match_rule m ON m.id = w.match_rule_id
            JOIN workflow_action_rule a ON a.id = w.action_rule_id
            WHERE w.id = #{id} AND w.deleted_at IS NULL
            """)
    DefinitionRow selectDefinition(@Param("id") long id);

    @Select("""
            SELECT count(*)
            FROM basic_project_member
            WHERE project_id = #{projectId} AND user_id = #{userId}
              AND role IN ('PROJECT_ADMIN', 'PROJECT_MANAGER')
              AND data_access_level IN ('READ_WRITE', 'FULL')
              AND (valid_from IS NULL OR CURRENT_TIMESTAMP >= valid_from)
              AND (valid_until IS NULL OR valid_until > CURRENT_TIMESTAMP)
            """)
    long countManagedProject(@Param("projectId") long projectId, @Param("userId") long userId);

    @Select("""
            INSERT INTO workflow_match_rule
                (name, description, scope, project_id, priority, enabled,
                 logic_operator, conditions_json, creator_id)
            VALUES
                (#{name}, #{description}, #{scope}, #{projectId}, #{priority}, #{enabled},
                 #{logicOperator}, #{conditionsJson}, #{creatorId})
            RETURNING id
            """)
    long insertMatchRule(
            @Param("name") String name,
            @Param("description") String description,
            @Param("scope") String scope,
            @Param("projectId") Long projectId,
            @Param("priority") int priority,
            @Param("enabled") boolean enabled,
            @Param("logicOperator") String logicOperator,
            @Param("conditionsJson") String conditionsJson,
            @Param("creatorId") long creatorId
    );

    @Update("""
            UPDATE workflow_match_rule
            SET name = #{name}, description = #{description}, scope = #{scope},
                project_id = #{projectId}, priority = #{priority}, enabled = #{enabled},
                logic_operator = #{logicOperator}, conditions_json = #{conditionsJson},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND deleted_at IS NULL
            """)
    int updateMatchRule(
            @Param("id") long id,
            @Param("name") String name,
            @Param("description") String description,
            @Param("scope") String scope,
            @Param("projectId") Long projectId,
            @Param("priority") int priority,
            @Param("enabled") boolean enabled,
            @Param("logicOperator") String logicOperator,
            @Param("conditionsJson") String conditionsJson
    );

    @Select("""
            INSERT INTO workflow_action_rule
                (name, description, scope, project_id, enabled, steps_json, creator_id)
            VALUES
                (#{name}, #{description}, #{scope}, #{projectId}, #{enabled},
                 #{stepsJson}, #{creatorId})
            RETURNING id
            """)
    long insertActionRule(
            @Param("name") String name,
            @Param("description") String description,
            @Param("scope") String scope,
            @Param("projectId") Long projectId,
            @Param("enabled") boolean enabled,
            @Param("stepsJson") String stepsJson,
            @Param("creatorId") long creatorId
    );

    @Update("""
            UPDATE workflow_action_rule
            SET name = #{name}, description = #{description}, scope = #{scope},
                project_id = #{projectId}, enabled = #{enabled}, steps_json = #{stepsJson},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND deleted_at IS NULL
            """)
    int updateActionRule(
            @Param("id") long id,
            @Param("name") String name,
            @Param("description") String description,
            @Param("scope") String scope,
            @Param("projectId") Long projectId,
            @Param("enabled") boolean enabled,
            @Param("stepsJson") String stepsJson
    );

    @Select("""
            INSERT INTO workflow_definition
                (name, description, scope, project_id, priority, enabled,
                 match_rule_id, action_rule_id, creator_id)
            VALUES
                (#{name}, #{description}, #{scope}, #{projectId}, #{priority}, #{enabled},
                 #{matchRuleId}, #{actionRuleId}, #{creatorId})
            RETURNING id
            """)
    long insertDefinition(
            @Param("name") String name,
            @Param("description") String description,
            @Param("scope") String scope,
            @Param("projectId") Long projectId,
            @Param("priority") int priority,
            @Param("enabled") boolean enabled,
            @Param("matchRuleId") long matchRuleId,
            @Param("actionRuleId") long actionRuleId,
            @Param("creatorId") long creatorId
    );

    @Update("""
            UPDATE workflow_definition
            SET name = #{name}, description = #{description}, scope = #{scope},
                project_id = #{projectId}, priority = #{priority}, enabled = #{enabled},
                match_rule_id = #{matchRuleId}, action_rule_id = #{actionRuleId},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND deleted_at IS NULL
            """)
    int updateDefinition(
            @Param("id") long id,
            @Param("name") String name,
            @Param("description") String description,
            @Param("scope") String scope,
            @Param("projectId") Long projectId,
            @Param("priority") int priority,
            @Param("enabled") boolean enabled,
            @Param("matchRuleId") long matchRuleId,
            @Param("actionRuleId") long actionRuleId
    );

    @Update("""
            UPDATE workflow_match_rule
            SET deleted_at = CURRENT_TIMESTAMP, enabled = FALSE, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND deleted_at IS NULL
            """)
    int deleteMatchRule(@Param("id") long id);

    @Update("""
            UPDATE workflow_action_rule
            SET deleted_at = CURRENT_TIMESTAMP, enabled = FALSE, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND deleted_at IS NULL
            """)
    int deleteActionRule(@Param("id") long id);

    @Update("""
            UPDATE workflow_definition
            SET deleted_at = CURRENT_TIMESTAMP, enabled = FALSE, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND deleted_at IS NULL
            """)
    int deleteDefinition(@Param("id") long id);

    @Select("""
            SELECT count(*) FROM workflow_definition
            WHERE deleted_at IS NULL
              AND (match_rule_id = #{id} OR action_rule_id = #{id})
            """)
    long countDefinitionReferences(@Param("id") long id);

    @Select("""
            SELECT p.id, p.name,
                   (#{admin} = TRUE OR EXISTS (
                       SELECT 1 FROM basic_project_member pm
                       WHERE pm.project_id = p.id AND pm.user_id = #{userId}
                         AND pm.role IN ('PROJECT_ADMIN', 'PROJECT_MANAGER')
                         AND pm.data_access_level IN ('READ_WRITE', 'FULL')
                         AND (pm.valid_from IS NULL OR CURRENT_TIMESTAMP >= pm.valid_from)
                         AND (pm.valid_until IS NULL OR pm.valid_until > CURRENT_TIMESTAMP)
                   )) AS manageable
            FROM basic_project p
            WHERE p.status != 'ARCHIVED'
              AND (#{admin} = TRUE OR EXISTS (
                  SELECT 1 FROM basic_project_member pm
                  WHERE pm.project_id = p.id AND pm.user_id = #{userId}
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
            <script>
            SELECT d.id, d.name, f.original_name, d.data_type, d.size_bytes,
                   d.robot_code, f.object_key, d.project_id, p.name AS project_name
            FROM data_dataset d
            JOIN file_metadata f ON f.id = d.file_id
            LEFT JOIN basic_project p ON p.id = d.project_id
            WHERE d.deleted = FALSE
              AND (#{admin} = TRUE OR d.uploader_id = #{userId} OR EXISTS (
                  SELECT 1 FROM basic_project_member pm
                  WHERE pm.project_id = d.project_id AND pm.user_id = #{userId}
                    AND (pm.valid_from IS NULL OR CURRENT_TIMESTAMP >= pm.valid_from)
                    AND (pm.valid_until IS NULL OR pm.valid_until > CURRENT_TIMESTAMP)
              ))
              <if test="projectId != null">AND d.project_id = #{projectId}</if>
              <if test="ids != null and ids.size() > 0">
                AND d.id IN
                <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
              </if>
            ORDER BY d.updated_at DESC, d.id DESC
            LIMIT 20000
            </script>
            """)
    List<DatasetRow> selectDatasets(
            @Param("userId") long userId,
            @Param("admin") boolean admin,
            @Param("projectId") Long projectId,
            @Param("ids") List<Long> ids
    );

    @Select("""
            SELECT
                (SELECT count(*) FROM workflow_definition w
                 WHERE w.deleted_at IS NULL AND (#{admin} = TRUE OR w.scope = 'GLOBAL' OR EXISTS (
                    SELECT 1 FROM basic_project_member pm
                    WHERE pm.project_id = w.project_id AND pm.user_id = #{userId}
                 ))) AS workflow_count,
                (SELECT count(*) FROM workflow_definition w
                 WHERE w.deleted_at IS NULL AND w.enabled = TRUE
                   AND (#{admin} = TRUE OR w.scope = 'GLOBAL' OR EXISTS (
                    SELECT 1 FROM basic_project_member pm
                    WHERE pm.project_id = w.project_id AND pm.user_id = #{userId}
                 ))) AS enabled_workflow_count,
                (SELECT count(*) FROM workflow_match_rule WHERE deleted_at IS NULL) AS match_rule_count,
                (SELECT count(*) FROM workflow_action_rule WHERE deleted_at IS NULL) AS action_rule_count,
                (SELECT count(*) FROM workflow_run r JOIN data_dataset d ON d.id = r.dataset_id
                 WHERE r.status IN ('QUEUED', 'RUNNING')
                   AND (#{admin} = TRUE OR d.uploader_id = #{userId} OR EXISTS (
                    SELECT 1 FROM basic_project_member pm
                    WHERE pm.project_id = d.project_id AND pm.user_id = #{userId}
                 ))) AS queued_run_count,
                (SELECT count(*) FROM workflow_run r JOIN data_dataset d ON d.id = r.dataset_id
                 WHERE r.status = 'FAILED'
                   AND (#{admin} = TRUE OR d.uploader_id = #{userId} OR EXISTS (
                    SELECT 1 FROM basic_project_member pm
                    WHERE pm.project_id = d.project_id AND pm.user_id = #{userId}
                 ))) AS failed_run_count
            """)
    OverviewRow selectOverview(@Param("userId") long userId, @Param("admin") boolean admin);

    @Select("""
            INSERT INTO workflow_run
                (workflow_id, dataset_id, stage, status, progress, trigger_type,
                 steps_snapshot_json, created_by)
            VALUES
                (#{workflowId}, #{datasetId}, 'MATCHING', 'QUEUED', 0, #{triggerType},
                 #{stepsJson}, #{createdBy})
            RETURNING id
            """)
    long insertRun(
            @Param("workflowId") long workflowId,
            @Param("datasetId") long datasetId,
            @Param("triggerType") String triggerType,
            @Param("stepsJson") String stepsJson,
            @Param("createdBy") long createdBy
    );

    @Select("""
            <script>
            SELECT r.id, r.workflow_id, w.name AS workflow_name, r.dataset_id,
                   d.name AS dataset_name, d.project_id, p.name AS project_name,
                   r.stage, r.status, r.progress, r.trigger_type, r.steps_snapshot_json,
                   r.error_message, r.started_at, r.completed_at, r.created_at
            FROM workflow_run r
            JOIN workflow_definition w ON w.id = r.workflow_id
            JOIN data_dataset d ON d.id = r.dataset_id
            LEFT JOIN basic_project p ON p.id = d.project_id
            WHERE (#{admin} = TRUE OR d.uploader_id = #{userId} OR EXISTS (
                SELECT 1 FROM basic_project_member pm
                WHERE pm.project_id = d.project_id AND pm.user_id = #{userId}
                  AND (pm.valid_from IS NULL OR CURRENT_TIMESTAMP >= pm.valid_from)
                  AND (pm.valid_until IS NULL OR pm.valid_until > CURRENT_TIMESTAMP)
            ))
              <if test="projectId != null">AND d.project_id = #{projectId}</if>
              <if test="status != null and status != ''">AND r.status = #{status}</if>
            ORDER BY r.created_at DESC, r.id DESC
            LIMIT 200
            </script>
            """)
    List<RunRow> selectRuns(
            @Param("userId") long userId,
            @Param("admin") boolean admin,
            @Param("projectId") Long projectId,
            @Param("status") String status
    );

    @Select("""
            SELECT r.id, r.workflow_id, w.name AS workflow_name, r.dataset_id,
                   d.name AS dataset_name, d.project_id, p.name AS project_name,
                   r.stage, r.status, r.progress, r.trigger_type, r.steps_snapshot_json,
                   r.error_message, r.started_at, r.completed_at, r.created_at
            FROM workflow_run r
            JOIN workflow_definition w ON w.id = r.workflow_id
            JOIN data_dataset d ON d.id = r.dataset_id
            LEFT JOIN basic_project p ON p.id = d.project_id
            WHERE r.id = #{id}
            """)
    RunRow selectRun(@Param("id") long id);
}

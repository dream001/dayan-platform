package com.dayan.platform.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dayan.platform.dto.CollectionTaskDtos.CollectionStepRequest;
import com.dayan.platform.model.CollectionTask;
import com.dayan.platform.repository.query.CollectionTaskDetailRows.AssigneeRow;
import com.dayan.platform.repository.query.CollectionTaskDetailRows.DatasetRow;
import com.dayan.platform.repository.query.CollectionTaskDetailRows.OptionRow;
import com.dayan.platform.repository.query.CollectionTaskDetailRows.StatusCountRow;
import com.dayan.platform.repository.query.CollectionTaskDetailRows.StepRow;
import com.dayan.platform.repository.query.CollectionTaskSummaryRow;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface CollectionTaskMapper extends BaseMapper<CollectionTask> {

    String ACCESS_CONDITION = """
            (#{platformAdmin} = TRUE
             OR t.created_by = #{userId}
             OR EXISTS (
                 SELECT 1 FROM data_collection_task_assignee own_assignee
                 WHERE own_assignee.task_id = t.id AND own_assignee.user_id = #{userId}
             )
             OR EXISTS (
                 SELECT 1 FROM basic_project_member own_member
                 WHERE own_member.project_id = t.project_id
                   AND own_member.user_id = #{userId}
                   AND (own_member.valid_from IS NULL OR CURRENT_TIMESTAMP >= own_member.valid_from)
                   AND (own_member.valid_until IS NULL OR own_member.valid_until > CURRENT_TIMESTAMP)
             ))
            """;

    String SUMMARY_COLUMNS = """
            t.*,
            p.name AS project_name,
            (SELECT count(*) FROM data_collection_task_dataset task_dataset
             WHERE task_dataset.task_id = t.id) AS collected_count,
            (SELECT dataset.name
             FROM data_collection_task_dataset task_dataset
             JOIN data_dataset dataset ON dataset.id = task_dataset.dataset_id
             WHERE task_dataset.task_id = t.id
             ORDER BY task_dataset.linked_at DESC, task_dataset.dataset_id DESC
             LIMIT 1) AS latest_file_name,
            (SELECT dataset.created_at
             FROM data_collection_task_dataset task_dataset
             JOIN data_dataset dataset ON dataset.id = task_dataset.dataset_id
             WHERE task_dataset.task_id = t.id
             ORDER BY task_dataset.linked_at DESC, task_dataset.dataset_id DESC
             LIMIT 1) AS latest_file_at,
            (SELECT COALESCE(jsonb_agg(value ORDER BY value), '[]'::jsonb)::text
             FROM (
                 SELECT DISTINCT u.display_name AS value
                 FROM data_collection_task_assignee task_assignee
                 JOIN sys_user u ON u.id = task_assignee.user_id
                 WHERE task_assignee.task_id = t.id
             ) assignee_names) AS assignees,
            (SELECT COALESCE(jsonb_agg(value ORDER BY sequence_no), '[]'::jsonb)::text
             FROM (
                 SELECT step.sequence_no,
                        COALESCE(NULLIF(step.action_name, ''), NULLIF(step.notes, '')) AS value
                 FROM data_collection_task_step step
                 WHERE step.task_id = t.id
             ) action_names) AS actions
            """;

    @Select("""
            <script>
            SELECT
            """ + SUMMARY_COLUMNS + """
            FROM data_collection_task t
            JOIN basic_project p ON p.id = t.project_id
            WHERE t.deleted_at IS NULL
              AND
            """ + ACCESS_CONDITION + """
              <if test="keyword != null and keyword != ''">
                AND t.name ILIKE '%' || #{keyword} || '%'
              </if>
              <if test="collectorId != null">
                AND EXISTS (
                    SELECT 1 FROM data_collection_task_assignee filter_assignee
                    WHERE filter_assignee.task_id = t.id
                      AND filter_assignee.user_id = #{collectorId}
                )
              </if>
              <if test="status != null and status != ''">AND t.status = #{status}</if>
            ORDER BY t.updated_at DESC, t.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<CollectionTaskSummaryRow> selectSummaryPage(
            @Param("userId") long userId,
            @Param("platformAdmin") boolean platformAdmin,
            @Param("keyword") String keyword,
            @Param("collectorId") Long collectorId,
            @Param("status") String status,
            @Param("offset") long offset,
            @Param("limit") int limit
    );

    @Select("""
            <script>
            SELECT count(*)
            FROM data_collection_task t
            WHERE t.deleted_at IS NULL
              AND
            """ + ACCESS_CONDITION + """
              <if test="keyword != null and keyword != ''">
                AND t.name ILIKE '%' || #{keyword} || '%'
              </if>
              <if test="collectorId != null">
                AND EXISTS (
                    SELECT 1 FROM data_collection_task_assignee filter_assignee
                    WHERE filter_assignee.task_id = t.id
                      AND filter_assignee.user_id = #{collectorId}
                )
              </if>
              <if test="status != null and status != ''">AND t.status = #{status}</if>
            </script>
            """)
    long countAccessible(
            @Param("userId") long userId,
            @Param("platformAdmin") boolean platformAdmin,
            @Param("keyword") String keyword,
            @Param("collectorId") Long collectorId,
            @Param("status") String status
    );

    @Select("""
            SELECT t.status, count(*) AS count
            FROM data_collection_task t
            WHERE t.deleted_at IS NULL
              AND
            """ + ACCESS_CONDITION + """
            GROUP BY t.status
            """)
    List<StatusCountRow> selectStatusCounts(
            @Param("userId") long userId,
            @Param("platformAdmin") boolean platformAdmin
    );

    @Select("""
            SELECT
            """ + SUMMARY_COLUMNS + """
            FROM data_collection_task t
            JOIN basic_project p ON p.id = t.project_id
            WHERE t.id = #{id}
              AND t.deleted_at IS NULL
              AND
            """ + ACCESS_CONDITION)
    CollectionTaskSummaryRow selectAccessibleById(
            @Param("id") long id,
            @Param("userId") long userId,
            @Param("platformAdmin") boolean platformAdmin
    );

    @Select("""
            <script>
            SELECT count(*)
            FROM data_collection_task
            WHERE lower(name) = lower(#{name})
            <if test="excludeId != null">AND id != #{excludeId}</if>
            </script>
            """)
    long countByName(
            @Param("name") String name,
            @Param("excludeId") Long excludeId
    );

    @Select("""
            SELECT u.id, u.username, u.display_name
            FROM data_collection_task_assignee task_assignee
            JOIN sys_user u ON u.id = task_assignee.user_id
            WHERE task_assignee.task_id = #{taskId}
            ORDER BY u.display_name, u.id
            """)
    List<AssigneeRow> selectAssignees(@Param("taskId") long taskId);

    @Select("""
            SELECT id, sequence_no, action_name, object_name, target_name, notes
            FROM data_collection_task_step
            WHERE task_id = #{taskId}
            ORDER BY sequence_no, id
            """)
    List<StepRow> selectSteps(@Param("taskId") long taskId);

    @Select("""
            SELECT dataset.id, dataset.name, dataset.size_bytes,
                   dataset.metadata_status AS status, dataset.created_at AS uploaded_at
            FROM data_collection_task_dataset task_dataset
            JOIN data_dataset dataset ON dataset.id = task_dataset.dataset_id
            WHERE task_dataset.task_id = #{taskId}
              AND dataset.deleted = FALSE
            ORDER BY task_dataset.linked_at DESC, dataset.id DESC
            """)
    List<DatasetRow> selectDatasets(@Param("taskId") long taskId);

    @Select("""
            SELECT p.id, p.name
            FROM basic_project p
            WHERE p.status IN ('PLANNING', 'ACTIVE')
              AND (
                #{platformAdmin} = TRUE
                OR p.owner_id = #{userId}
                OR EXISTS (
                    SELECT 1
                    FROM basic_project_member member
                    WHERE member.project_id = p.id
                      AND member.user_id = #{userId}
                      AND member.role IN ('PROJECT_ADMIN', 'PROJECT_MANAGER')
                      AND member.data_access_level IN ('READ_WRITE', 'FULL')
                      AND (member.valid_from IS NULL OR CURRENT_TIMESTAMP >= member.valid_from)
                      AND (member.valid_until IS NULL OR member.valid_until > CURRENT_TIMESTAMP)
                )
              )
            ORDER BY p.name, p.id
            """)
    List<OptionRow> selectProjectOptions(
            @Param("userId") long userId,
            @Param("platformAdmin") boolean platformAdmin
    );

    @Select("""
            SELECT DISTINCT u.id, u.username, u.display_name
            FROM basic_project_member member
            JOIN sys_user u ON u.id = member.user_id AND u.enabled = TRUE
            JOIN sys_user_role ur ON ur.user_id = u.id
            JOIN sys_role role ON role.id = ur.role_id
                              AND role.code = 'COLLECTOR'
                              AND role.enabled = TRUE
            WHERE member.project_id = #{projectId}
              AND (member.valid_from IS NULL OR CURRENT_TIMESTAMP >= member.valid_from)
              AND (member.valid_until IS NULL OR member.valid_until > CURRENT_TIMESTAMP)
            ORDER BY u.display_name, u.id
            """)
    List<OptionRow> selectCollectorOptions(@Param("projectId") long projectId);

    @Select("""
            SELECT DISTINCT u.id, u.username, u.display_name
            FROM basic_project_member member
            JOIN basic_project p ON p.id = member.project_id
            JOIN sys_user u ON u.id = member.user_id AND u.enabled = TRUE
            JOIN sys_user_role ur ON ur.user_id = u.id
            JOIN sys_role role ON role.id = ur.role_id
                              AND role.code = 'COLLECTOR'
                              AND role.enabled = TRUE
            WHERE (#{platformAdmin} = TRUE OR p.owner_id = #{userId}
                   OR EXISTS (
                       SELECT 1
                       FROM basic_project_member current_member
                       WHERE current_member.project_id = p.id
                         AND current_member.user_id = #{userId}
                         AND (current_member.valid_from IS NULL
                              OR CURRENT_TIMESTAMP >= current_member.valid_from)
                         AND (current_member.valid_until IS NULL
                              OR current_member.valid_until > CURRENT_TIMESTAMP)
                   ))
              AND (member.valid_from IS NULL OR CURRENT_TIMESTAMP >= member.valid_from)
              AND (member.valid_until IS NULL OR member.valid_until > CURRENT_TIMESTAMP)
            ORDER BY u.display_name, u.id
            """)
    List<OptionRow> selectAccessibleCollectorOptions(
            @Param("userId") long userId,
            @Param("platformAdmin") boolean platformAdmin
    );

    @Select("""
            <script>
            SELECT count(DISTINCT u.id)
            FROM basic_project_member member
            JOIN sys_user u ON u.id = member.user_id AND u.enabled = TRUE
            JOIN sys_user_role ur ON ur.user_id = u.id
            JOIN sys_role role ON role.id = ur.role_id
                              AND role.code = 'COLLECTOR'
                              AND role.enabled = TRUE
            WHERE member.project_id = #{projectId}
              AND u.id IN
              <foreach collection="userIds" item="userId" open="(" separator="," close=")">
                #{userId}
              </foreach>
              AND (member.valid_from IS NULL OR CURRENT_TIMESTAMP >= member.valid_from)
              AND (member.valid_until IS NULL OR member.valid_until > CURRENT_TIMESTAMP)
            </script>
            """)
    long countEligibleCollectors(
            @Param("projectId") long projectId,
            @Param("userIds") Set<Long> userIds
    );

    @Insert("""
            <script>
            INSERT INTO data_collection_task_assignee (task_id, user_id)
            VALUES
            <foreach collection="userIds" item="userId" separator=",">
                (#{taskId}, #{userId})
            </foreach>
            </script>
            """)
    int insertAssignees(
            @Param("taskId") long taskId,
            @Param("userIds") Set<Long> userIds
    );

    @Insert("""
            <script>
            INSERT INTO data_collection_task_step
                (task_id, sequence_no, action_name, object_name, target_name, notes)
            VALUES
            <foreach collection="steps" item="step" index="index" separator=",">
                (#{taskId}, #{index} + 1, #{step.actionName}, #{step.objectName},
                 #{step.targetName}, #{step.notes})
            </foreach>
            </script>
            """)
    int insertSteps(
            @Param("taskId") long taskId,
            @Param("steps") List<CollectionStepRequest> steps
    );

    @Insert("""
            INSERT INTO data_collection_task_dataset (task_id, dataset_id, linked_by)
            VALUES (#{taskId}, #{datasetId}, #{linkedBy})
            ON CONFLICT (task_id, dataset_id) DO NOTHING
            """)
    int linkDataset(
            @Param("taskId") long taskId,
            @Param("datasetId") long datasetId,
            @Param("linkedBy") long linkedBy
    );

    @Delete("DELETE FROM data_collection_task_assignee WHERE task_id = #{taskId}")
    int deleteAssignees(@Param("taskId") long taskId);

    @Delete("DELETE FROM data_collection_task_step WHERE task_id = #{taskId}")
    int deleteSteps(@Param("taskId") long taskId);

    @Delete("""
            DELETE FROM data_collection_task_dataset
            WHERE task_id = #{taskId} AND dataset_id = #{datasetId}
            """)
    int unlinkDataset(
            @Param("taskId") long taskId,
            @Param("datasetId") long datasetId
    );

    @Update("""
            UPDATE data_collection_task
            SET deleted_at = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND deleted_at IS NULL
            """)
    int softDelete(@Param("id") long id);
}

package com.dayan.platform.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dayan.platform.model.AnnotationTask;
import com.dayan.platform.repository.query.AnnotationDatasetRow;
import com.dayan.platform.repository.query.AnnotationStatusCountRow;
import com.dayan.platform.repository.query.AnnotationTaskSummaryRow;
import com.dayan.platform.vo.AnnotationTaskViews.DatasetOption;
import com.dayan.platform.vo.AnnotationTaskViews.PersonOption;
import com.dayan.platform.vo.AnnotationTaskViews.ProjectOption;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface AnnotationTaskWorkflowMapper extends BaseMapper<AnnotationTask> {

    String SUMMARY_SELECT = """
            SELECT t.*, p.name AS project_name,
                   annotator.display_name AS annotator_name,
                   reviewer.display_name AS reviewer_name,
                   creator.display_name AS creator_name,
                   count(td.id) FILTER (WHERE td.unassigned_at IS NULL) AS dataset_count,
                   count(td.id) FILTER (
                       WHERE td.unassigned_at IS NULL AND td.check_result IS NOT NULL
                   ) AS reviewed_count
            FROM data_annotation_task t
            JOIN basic_project p ON p.id = t.project_id
            JOIN sys_user annotator ON annotator.id = t.annotator_id
            LEFT JOIN sys_user reviewer ON reviewer.id = t.reviewer_id
            JOIN sys_user creator ON creator.id = t.creator_id
            LEFT JOIN data_annotation_task_dataset td ON td.task_id = t.id
            """;

    String ACCESS_PREDICATE = """
            (#{platformAdmin} = TRUE
             OR t.creator_id = #{userId}
             OR t.annotator_id = #{userId}
             OR t.reviewer_id = #{userId}
             OR EXISTS (
                 SELECT 1
                 FROM basic_project_member pm
                 WHERE pm.project_id = t.project_id
                   AND pm.user_id = #{userId}
                   AND (pm.valid_from IS NULL OR CURRENT_TIMESTAMP >= pm.valid_from)
                   AND (pm.valid_until IS NULL OR pm.valid_until > CURRENT_TIMESTAMP)
             ))
            """;

    @Select("""
            <script>
            """ + SUMMARY_SELECT + """
            WHERE t.deleted_at IS NULL
              AND """ + ACCESS_PREDICATE + """
              <if test="keyword != null and keyword != ''">
                AND t.name ILIKE '%' || #{keyword} || '%'
              </if>
              <if test="status != null and status != ''">AND t.status = #{status}</if>
              <if test="projectId != null">AND t.project_id = #{projectId}</if>
              <if test="annotatorId != null">AND t.annotator_id = #{annotatorId}</if>
              <if test="reviewerId != null">AND t.reviewer_id = #{reviewerId}</if>
              <if test="createdDate != null">AND t.created_at::date = CAST(#{createdDate} AS date)</if>
            GROUP BY t.id, p.name, annotator.display_name, reviewer.display_name, creator.display_name
            ORDER BY t.created_at DESC, t.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<AnnotationTaskSummaryRow> selectSummaryPage(
            @Param("userId") long userId,
            @Param("platformAdmin") boolean platformAdmin,
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("projectId") Long projectId,
            @Param("annotatorId") Long annotatorId,
            @Param("reviewerId") Long reviewerId,
            @Param("createdDate") String createdDate,
            @Param("offset") long offset,
            @Param("limit") int limit
    );

    @Select("""
            <script>
            SELECT count(*)
            FROM data_annotation_task t
            WHERE t.deleted_at IS NULL
              AND """ + ACCESS_PREDICATE + """
              <if test="keyword != null and keyword != ''">
                AND t.name ILIKE '%' || #{keyword} || '%'
              </if>
              <if test="status != null and status != ''">AND t.status = #{status}</if>
              <if test="projectId != null">AND t.project_id = #{projectId}</if>
              <if test="annotatorId != null">AND t.annotator_id = #{annotatorId}</if>
              <if test="reviewerId != null">AND t.reviewer_id = #{reviewerId}</if>
              <if test="createdDate != null">AND t.created_at::date = CAST(#{createdDate} AS date)</if>
            </script>
            """)
    long countSummaries(
            @Param("userId") long userId,
            @Param("platformAdmin") boolean platformAdmin,
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("projectId") Long projectId,
            @Param("annotatorId") Long annotatorId,
            @Param("reviewerId") Long reviewerId,
            @Param("createdDate") String createdDate
    );

    @Select("""
            """ + SUMMARY_SELECT + """
            WHERE t.id = #{id}
              AND t.deleted_at IS NULL
            GROUP BY t.id, p.name, annotator.display_name, reviewer.display_name, creator.display_name
            """)
    AnnotationTaskSummaryRow selectSummaryById(@Param("id") long id);

    @Select("""
            SELECT t.status, count(*) AS count
            FROM data_annotation_task t
            WHERE t.deleted_at IS NULL
              AND """ + ACCESS_PREDICATE + """
            GROUP BY t.status
            """)
    List<AnnotationStatusCountRow> selectStatusCounts(
            @Param("userId") long userId,
            @Param("platformAdmin") boolean platformAdmin
    );

    @Select("""
            SELECT td.id, td.dataset_id, d.name, d.data_type, d.size_bytes,
                   td.annotation_description, td.check_result, td.rejection_reason, d.created_at
            FROM data_annotation_task_dataset td
            JOIN data_dataset d ON d.id = td.dataset_id
            WHERE td.task_id = #{taskId}
              AND td.unassigned_at IS NULL
            ORDER BY td.assigned_at, td.id
            """)
    List<AnnotationDatasetRow> selectDatasets(@Param("taskId") long taskId);

    @Select("SELECT count(*) FROM data_annotation_task WHERE lower(name) = lower(#{name})")
    long countByName(@Param("name") String name);

    @Select("""
            SELECT p.id, p.name
            FROM basic_project p
            WHERE p.status IN ('PLANNING', 'ACTIVE')
              AND (#{platformAdmin} = TRUE OR EXISTS (
                  SELECT 1
                  FROM basic_project_member pm
                  WHERE pm.project_id = p.id
                    AND pm.user_id = #{userId}
                    AND pm.role IN ('PROJECT_ADMIN', 'PROJECT_MANAGER')
                    AND (pm.valid_from IS NULL OR CURRENT_TIMESTAMP >= pm.valid_from)
                    AND (pm.valid_until IS NULL OR pm.valid_until > CURRENT_TIMESTAMP)
              ))
            ORDER BY p.name, p.id
            """)
    List<ProjectOption> selectProjectOptions(
            @Param("userId") long userId,
            @Param("platformAdmin") boolean platformAdmin
    );

    @Select("""
            SELECT u.id, u.username, u.display_name
            FROM basic_project_member pm
            JOIN sys_user u ON u.id = pm.user_id AND u.enabled = TRUE
            WHERE pm.project_id = #{projectId}
              AND pm.role = #{role}
              AND (pm.valid_from IS NULL OR CURRENT_TIMESTAMP >= pm.valid_from)
              AND (pm.valid_until IS NULL OR pm.valid_until > CURRENT_TIMESTAMP)
            ORDER BY u.display_name, u.id
            """)
    List<PersonOption> selectPeople(
            @Param("projectId") long projectId,
            @Param("role") String role
    );

    @Select("""
            <script>
            SELECT d.id, d.project_id, d.name, d.data_type, d.size_bytes, d.created_at
            FROM data_dataset d
            WHERE d.deleted = FALSE
              AND d.metadata_status = 'READY'
              AND d.project_id IS NOT NULL
              AND NOT EXISTS (
                  SELECT 1 FROM data_annotation_task_dataset td
                  WHERE td.dataset_id = d.id AND td.unassigned_at IS NULL
              )
              <if test="projectId != null">AND d.project_id = #{projectId}</if>
            ORDER BY d.created_at DESC, d.id DESC
            LIMIT 500
            </script>
            """)
    List<DatasetOption> selectAvailableDatasets(@Param("projectId") Long projectId);

    @Select("""
            <script>
            SELECT id
            FROM data_dataset
            WHERE project_id = #{projectId}
              AND deleted = FALSE
              AND metadata_status = 'READY'
              AND id IN
              <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
              AND NOT EXISTS (
                  SELECT 1 FROM data_annotation_task_dataset td
                  WHERE td.dataset_id = data_dataset.id AND td.unassigned_at IS NULL
              )
            ORDER BY id
            FOR UPDATE
            </script>
            """)
    List<Long> lockAvailableDatasetIds(
            @Param("projectId") long projectId,
            @Param("ids") Collection<Long> ids
    );

    @Insert("""
            <script>
            INSERT INTO data_annotation_task_dataset (task_id, dataset_id)
            VALUES
            <foreach collection="datasetIds" item="datasetId" separator=",">
                (#{taskId}, #{datasetId})
            </foreach>
            </script>
            """)
    int assignDatasets(
            @Param("taskId") long taskId,
            @Param("datasetIds") Collection<Long> datasetIds
    );

    @Update("""
            <script>
            UPDATE data_dataset
            SET task_id = #{taskId},
                annotation_status = 'ASSIGNED',
                updated_at = CURRENT_TIMESTAMP
            WHERE id IN
            <foreach collection="datasetIds" item="datasetId" open="(" separator="," close=")">
                #{datasetId}
            </foreach>
            </script>
            """)
    int markDatasetsAssigned(
            @Param("taskId") long taskId,
            @Param("datasetIds") Collection<Long> datasetIds
    );

    @Update("""
            UPDATE data_annotation_task
            SET dataset_count = (
                    SELECT count(*)
                    FROM data_annotation_task_dataset
                    WHERE task_id = #{taskId} AND unassigned_at IS NULL
                ),
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{taskId}
            """)
    int syncDatasetCount(@Param("taskId") long taskId);

    @Update("""
            UPDATE data_annotation_task_dataset
            SET check_result = #{result}, rejection_reason = #{reason}
            WHERE id = #{relationId} AND task_id = #{taskId} AND unassigned_at IS NULL
            """)
    int reviewDataset(
            @Param("taskId") long taskId,
            @Param("relationId") long relationId,
            @Param("result") String result,
            @Param("reason") String reason
    );

    @Update("""
            UPDATE data_annotation_task_dataset
            SET annotation_description = #{description}, check_result = NULL, rejection_reason = NULL
            WHERE task_id = #{taskId} AND unassigned_at IS NULL
            """)
    int quickAnnotate(
            @Param("taskId") long taskId,
            @Param("description") String description
    );

    @Update("""
            UPDATE data_annotation_task_dataset target
            SET annotation_description = source.annotation_description,
                check_result = NULL,
                rejection_reason = NULL
            FROM data_annotation_task_dataset source
            WHERE target.task_id = #{taskId}
              AND target.unassigned_at IS NULL
              AND source.id = #{sourceRelationId}
              AND source.task_id = #{taskId}
              AND source.unassigned_at IS NULL
            """)
    int copyAnnotation(
            @Param("taskId") long taskId,
            @Param("sourceRelationId") long sourceRelationId
    );

    @Update("""
            UPDATE data_annotation_task_dataset
            SET annotation_description = replace(annotation_description, #{findText}, #{replaceText}),
                check_result = NULL,
                rejection_reason = NULL
            WHERE task_id = #{taskId}
              AND unassigned_at IS NULL
              AND annotation_description IS NOT NULL
              AND position(#{findText} IN annotation_description) > 0
            """)
    int replaceAnnotation(
            @Param("taskId") long taskId,
            @Param("findText") String findText,
            @Param("replaceText") String replaceText
    );

    @Update("""
            UPDATE data_annotation_task_dataset
            SET unassigned_at = CURRENT_TIMESTAMP
            WHERE task_id = #{taskId} AND unassigned_at IS NULL
            """)
    int unassignDatasets(@Param("taskId") long taskId);

    @Update("""
            UPDATE data_dataset
            SET task_id = NULL,
                annotation_status = 'UNASSIGNED',
                updated_at = CURRENT_TIMESTAMP
            WHERE task_id = #{taskId}
            """)
    int markDatasetsUnassigned(@Param("taskId") long taskId);
}

package com.dayan.platform.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dayan.platform.dto.DatasetFilter;
import com.dayan.platform.model.Dataset;
import com.dayan.platform.repository.query.AnnotationAggRow;
import com.dayan.platform.repository.query.DatasetListRow;
import com.dayan.platform.repository.query.DatasetSelectionAggRow;
import com.dayan.platform.repository.query.OptionRow;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface DatasetMapper extends BaseMapper<Dataset> {

    String FILTERS = """
            <choose>
              <when test="scope == 'PERSONAL'">
                AND d.uploader_id = #{currentUserId}
              </when>
              <when test="scope == 'PROJECT'">
                AND d.project_id = #{projectId}
                <if test="!admin">
                  AND (
                    d.uploader_id = #{currentUserId}
                    OR d.project_id IN (
                      SELECT pm.project_id FROM basic_project_member pm
                      WHERE pm.user_id = #{currentUserId}
                    )
                    OR d.project_id IN (
                      SELECT bp.id FROM basic_project bp
                      WHERE bp.access_level = 'PUBLIC' AND bp.status = 'ACTIVE'
                    )
                  )
                </if>
              </when>
              <otherwise>
                <if test="!admin">
                  AND (
                    d.uploader_id = #{currentUserId}
                    OR d.project_id IN (
                      SELECT pm.project_id FROM basic_project_member pm
                      WHERE pm.user_id = #{currentUserId}
                    )
                    OR d.project_id IN (
                      SELECT bp.id FROM basic_project bp
                      WHERE bp.access_level = 'PUBLIC' AND bp.status = 'ACTIVE'
                    )
                  )
                </if>
              </otherwise>
            </choose>
            <if test="name != null and name != ''">
              AND d.name ILIKE CONCAT('%', #{name}, '%')
            </if>
            <if test="robotCode != null and robotCode != ''">
              AND d.robot_code = #{robotCode}
            </if>
            <if test="tag != null and tag != ''">
              AND EXISTS (
                SELECT 1 FROM data_dataset_tag_rel r
                JOIN data_dataset_tag t ON t.id = r.tag_id
                WHERE r.dataset_id = d.id AND t.name = #{tag}
              )
            </if>
            <if test="uploaderId != null">
              AND d.uploader_id = #{uploaderId}
            </if>
            <if test="projectIds != null and projectIds.size() > 0">
              AND d.project_id IN
              <foreach collection="projectIds" item="pid" open="(" separator="," close=")">#{pid}</foreach>
            </if>
            <if test="collectorIds != null and collectorIds.size() > 0">
              AND d.collector_id IN
              <foreach collection="collectorIds" item="cid" open="(" separator="," close=")">#{cid}</foreach>
            </if>
            <if test="sourceTaskCode != null and sourceTaskCode != ''">
              AND d.source_task_code = #{sourceTaskCode}
            </if>
            <if test="minDuration != null">
              AND d.duration_seconds &gt;= #{minDuration}
            </if>
            <if test="maxDuration != null">
              AND d.duration_seconds &lt;= #{maxDuration}
            </if>
            <if test="annotationText != null and annotationText != ''">
              AND EXISTS (
                SELECT 1 FROM data_annotation a
                WHERE a.dataset_id = d.id
                  AND a.content_text ILIKE CONCAT('%', #{annotationText}, '%')
              )
            </if>
            """;

    String TAG_SELECT = """
            (SELECT string_agg(t.name, E'\n')
               FROM data_dataset_tag_rel r
               JOIN data_dataset_tag t ON t.id = r.tag_id
              WHERE r.dataset_id = d.id) AS tag_names
            """;

    @Select("""
            <script>
            SELECT
                d.*,
                p.name AS project_name,
                u.display_name AS uploader_name,
                c.display_name AS collector_name,
            """ + TAG_SELECT + """
            FROM data_dataset d
            LEFT JOIN basic_project p ON p.id = d.project_id
            LEFT JOIN sys_user u ON u.id = d.uploader_id
            LEFT JOIN sys_user c ON c.id = d.collector_id
            WHERE d.deleted = FALSE
            """ + FILTERS + """
            ORDER BY d.created_at ${sortDir}, d.id ${sortDir}
            LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<DatasetListRow> selectFilteredPage(DatasetFilter filter);

    @Select("""
            <script>
            SELECT count(*)
            FROM data_dataset d
            WHERE d.deleted = FALSE
            """ + FILTERS + """
            </script>
            """)
    long countFiltered(DatasetFilter filter);

    @Select("""
            <script>
            SELECT COALESCE(sum(d.size_bytes), 0)
            FROM data_dataset d
            WHERE d.deleted = FALSE
            """ + FILTERS + """
            </script>
            """)
    long sumSizeFiltered(DatasetFilter filter);

    @Select("""
            SELECT
                d.*,
                p.name AS project_name,
                u.display_name AS uploader_name,
                c.display_name AS collector_name,
            """ + TAG_SELECT + """
            FROM data_dataset d
            LEFT JOIN basic_project p ON p.id = d.project_id
            LEFT JOIN sys_user u ON u.id = d.uploader_id
            LEFT JOIN sys_user c ON c.id = d.collector_id
            WHERE d.deleted = FALSE AND d.id = #{id}
            """)
    DatasetListRow selectActiveById(@Param("id") long id);

    @Select("""
            <script>
            SELECT
                d.*,
                p.name AS project_name,
                u.display_name AS uploader_name,
                c.display_name AS collector_name,
            """ + TAG_SELECT + """
            FROM data_dataset d
            LEFT JOIN basic_project p ON p.id = d.project_id
            LEFT JOIN sys_user u ON u.id = d.uploader_id
            LEFT JOIN sys_user c ON c.id = d.collector_id
            WHERE d.deleted = TRUE
            <if test="!admin">AND d.uploader_id = #{currentUserId}</if>
            ORDER BY d.deleted_at DESC, d.id DESC
            </script>
            """)
    List<DatasetListRow> selectDeleted(DatasetFilter filter);

    @Select("""
            <script>
            SELECT count(*) AS total,
                   COALESCE(sum(duration_seconds), 0) AS total_duration
            FROM data_dataset
            WHERE deleted = FALSE AND id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    DatasetSelectionAggRow selectSelectionAgg(@Param("ids") List<Long> ids);

    @Select("""
            SELECT count(*)
            FROM basic_project_member
            WHERE project_id = #{projectId} AND user_id = #{userId}
            """)
    long countMembership(@Param("projectId") long projectId, @Param("userId") long userId);

    @Select("""
            SELECT DISTINCT robot_code AS name
            FROM data_dataset
            WHERE deleted = FALSE
              AND robot_code IS NOT NULL AND btrim(robot_code) &lt;&gt; ''
            ORDER BY name
            """)
    List<String> selectRobotCodes();

    @Select("""
            SELECT id, display_name AS name
            FROM sys_user
            WHERE enabled = TRUE
            ORDER BY display_name, id
            """)
    List<OptionRow> selectUserOptions();

    @Select("""
            SELECT * FROM data_dataset
            WHERE deleted = FALSE AND lower(name) = lower(#{name})
            """)
    Dataset selectActiveByName(@Param("name") String name);

    @Select("""
            SELECT * FROM data_dataset
            WHERE deleted = TRUE AND lower(name) = lower(#{name})
            ORDER BY deleted_at DESC, id DESC
            LIMIT 1
            """)
    Dataset selectDeletedByName(@Param("name") String name);

    @Select("SELECT count(*) FROM basic_project WHERE id = #{id}")
    long countProjectById(@Param("id") long id);

    @Select("""
            SELECT count(*)
            FROM basic_project p
            WHERE p.id = #{projectId}
              AND (
                (p.access_level = 'PUBLIC' AND p.status = 'ACTIVE')
                OR p.id IN (
                    SELECT project_id FROM basic_project_member
                    WHERE user_id = #{userId}
                )
              )
            """)
    long countAccessibleProject(
            @Param("projectId") long projectId,
            @Param("userId") long userId
    );

    @Update("""
            UPDATE data_dataset
            SET deleted_at = NULL,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int clearDeletedAt(@Param("id") long id);
}

package com.dayan.platform.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dayan.platform.model.Robot;
import com.dayan.platform.repository.query.RobotRows.CatalogRow;
import com.dayan.platform.repository.query.RobotRows.DatasetRow;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface RobotMapper extends BaseMapper<Robot> {

    String DATASET_VISIBILITY = """
            AND (
              #{admin} = TRUE
              OR d.uploader_id = #{userId}
              OR d.project_id IN (
                SELECT pm.project_id
                FROM basic_project_member pm
                WHERE pm.user_id = #{userId}
              )
              OR d.project_id IN (
                SELECT p.id
                FROM basic_project p
                WHERE p.access_level = 'PUBLIC' AND p.status = 'ACTIVE'
              )
            )
            """;

    @Select("""
            <script>
            SELECT r.*,
                   (
                     SELECT count(*)
                     FROM data_dataset d
                     WHERE d.deleted = FALSE
                       AND d.robot_code = r.name
            """ + DATASET_VISIBILITY + """
                   ) AS dataset_count
            FROM basic_robot r
            WHERE r.deleted = FALSE
            <if test="robotType != null and robotType != ''">
              AND r.robot_type = #{robotType}
            </if>
            ORDER BY r.built_in DESC, r.name ASC, r.id ASC
            </script>
            """)
    List<CatalogRow> selectCatalog(
            @Param("robotType") String robotType,
            @Param("userId") long userId,
            @Param("admin") boolean admin
    );

    @Select("""
            SELECT *
            FROM basic_robot
            WHERE id = #{id} AND deleted = FALSE
            """)
    Robot selectActiveById(@Param("id") long id);

    @Select("""
            SELECT *
            FROM basic_robot
            WHERE lower(name) = lower(#{name})
            LIMIT 1
            """)
    Robot selectAnyByName(@Param("name") String name);

    @Select("""
            SELECT name
            FROM basic_robot
            WHERE deleted = FALSE
            ORDER BY name
            """)
    List<String> selectActiveNames();

    @Update("""
            UPDATE basic_robot
            SET deleted = TRUE,
                deleted_at = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND deleted = FALSE
            """)
    int softDelete(@Param("id") long id);

    @Select("""
            SELECT d.id,
                   d.name,
                   d.data_type,
                   d.size_bytes,
                   d.duration_seconds,
                   u.display_name AS uploader_name,
                   d.created_at AS uploaded_at,
                   (SELECT count(*) FROM data_annotation a WHERE a.dataset_id = d.id)
                       AS annotation_count
            FROM data_dataset d
            JOIN sys_user u ON u.id = d.uploader_id
            WHERE d.deleted = FALSE
              AND d.robot_code = #{robotName}
            """ + DATASET_VISIBILITY + """
            ORDER BY d.created_at DESC, d.id DESC
            """)
    List<DatasetRow> selectDatasets(
            @Param("robotName") String robotName,
            @Param("userId") long userId,
            @Param("admin") boolean admin
    );
}

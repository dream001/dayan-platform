package com.dayan.platform.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dayan.platform.model.DatasetTag;
import com.dayan.platform.repository.query.DatasetTagRow;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface DatasetTagMapper extends BaseMapper<DatasetTag> {

    @Select("SELECT * FROM data_dataset_tag WHERE name = #{name}")
    DatasetTag findByName(@Param("name") String name);

    @Select("SELECT name FROM data_dataset_tag ORDER BY name")
    List<String> selectAllTagNames();

    @Insert("""
            INSERT INTO data_dataset_tag_rel (dataset_id, tag_id)
            VALUES (#{datasetId}, #{tagId})
            ON CONFLICT DO NOTHING
            """)
    int insertRel(@Param("datasetId") long datasetId, @Param("tagId") long tagId);

    @Delete("""
            DELETE FROM data_dataset_tag_rel
            WHERE dataset_id = #{datasetId} AND tag_id = #{tagId}
            """)
    int deleteRel(@Param("datasetId") long datasetId, @Param("tagId") long tagId);

    @Select("""
            <script>
            SELECT r.dataset_id AS datasetId, t.name AS tagName
            FROM data_dataset_tag_rel r
            JOIN data_dataset_tag t ON t.id = r.tag_id
            WHERE r.dataset_id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            ORDER BY t.name
            </script>
            """)
    List<DatasetTagRow> selectTagsForDatasets(@Param("ids") List<Long> ids);

    @Delete("""
            DELETE FROM data_dataset_tag
            WHERE id IN (
                SELECT t.id
                FROM data_dataset_tag t
                LEFT JOIN data_dataset_tag_rel r ON r.tag_id = t.id
                WHERE r.tag_id IS NULL
            )
            """)
    int deleteUnusedTags();
}

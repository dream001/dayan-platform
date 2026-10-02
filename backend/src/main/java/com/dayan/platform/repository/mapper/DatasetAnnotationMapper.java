package com.dayan.platform.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dayan.platform.model.DatasetAnnotation;
import com.dayan.platform.repository.query.AnnotationAggRow;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface DatasetAnnotationMapper extends BaseMapper<DatasetAnnotation> {

    String AGG_SELECT = """
            SELECT
                count(*) AS total_annotations,
                count(*) FILTER (WHERE is_qualified = TRUE AND is_valid = TRUE)
                    AS qualified_annotations,
                COALESCE(sum(covered_duration_seconds) FILTER (WHERE is_valid = TRUE), 0)
                    AS covered_duration,
                count(*) FILTER (WHERE reviewed = TRUE) AS reviewed_count,
                count(*) FILTER (WHERE reviewed = TRUE AND is_qualified = TRUE)
                    AS reviewed_qualified,
                count(*) FILTER (WHERE invalid_collect = TRUE) AS invalid_collect,
                count(*) FILTER (WHERE semantic_error = TRUE AND semantic_corrected = FALSE)
                    AS semantic_uncorrected,
                count(*) FILTER (WHERE semantic_error = TRUE AND semantic_corrected = TRUE)
                    AS semantic_corrected,
                count(DISTINCT dataset_id) FILTER (WHERE is_valid = FALSE)
                    AS invalid_dataset_count
            FROM data_annotation
            WHERE dataset_id IN
            """;

    @Select("""
            <script>
            """ + AGG_SELECT + """
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    AnnotationAggRow aggregateForDatasets(@Param("ids") List<Long> ids);

    @Select("""
            SELECT count(*)
            FROM data_annotation
            WHERE dataset_id = #{id} AND is_valid = FALSE
            """)
    long countInvalidForDataset(@Param("id") long id);
}

package com.dayan.platform.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dayan.platform.model.AnnotationTask;
import com.dayan.platform.repository.query.TaskDetailRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface AnnotationTaskMapper extends BaseMapper<AnnotationTask> {

    @Select("""
            SELECT t.*, u.display_name AS creator_name
            FROM data_annotation_task t
            LEFT JOIN sys_user u ON u.id = t.creator_id
            WHERE t.id = #{id}
            """)
    TaskDetailRow selectTaskRow(@Param("id") long id);
}

package com.dayan.platform.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dayan.platform.model.Department;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface DepartmentMapper extends BaseMapper<Department> {

    @Select("SELECT count(*) FROM sys_department WHERE parent_id = #{id}")
    long countChildren(@Param("id") long id);

    @Select("SELECT count(*) FROM sys_user WHERE department_id = #{id}")
    long countUsers(@Param("id") long id);

    @Select("""
            WITH RECURSIVE descendants AS (
                SELECT id FROM sys_department WHERE parent_id = #{id}
                UNION ALL
                SELECT d.id
                FROM sys_department d
                JOIN descendants x ON d.parent_id = x.id
            )
            SELECT count(*) FROM descendants WHERE id = #{candidateParentId}
            """)
    long countDescendant(
            @Param("id") long id,
            @Param("candidateParentId") long candidateParentId
    );
}

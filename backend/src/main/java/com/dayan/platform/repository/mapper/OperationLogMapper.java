package com.dayan.platform.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dayan.platform.model.OperationLog;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface OperationLogMapper extends BaseMapper<OperationLog> {

    @Select("""
            <script>
            SELECT *
            FROM operation_log
            <where>
              <if test="userId != null">AND operator_id = #{userId}</if>
              <if test="user != null and user != ''">
                AND operator_name ILIKE CONCAT('%', #{user}, '%')
              </if>
              <if test="module != null and module != ''">
                AND upper(module) = upper(#{module})
              </if>
              <if test="result != null and result != ''">AND result = #{result}</if>
              <if test="startTime != null">AND occurred_at &gt;= #{startTime}</if>
              <if test="endTime != null">AND occurred_at &lt;= #{endTime}</if>
            </where>
            ORDER BY occurred_at DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<OperationLog> selectPage(
            @Param("userId") Long userId,
            @Param("user") String user,
            @Param("module") String module,
            @Param("result") String result,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("limit") int limit,
            @Param("offset") long offset
    );

    @Select("""
            <script>
            SELECT count(*)
            FROM operation_log
            <where>
              <if test="userId != null">AND operator_id = #{userId}</if>
              <if test="user != null and user != ''">
                AND operator_name ILIKE CONCAT('%', #{user}, '%')
              </if>
              <if test="module != null and module != ''">
                AND upper(module) = upper(#{module})
              </if>
              <if test="result != null and result != ''">AND result = #{result}</if>
              <if test="startTime != null">AND occurred_at &gt;= #{startTime}</if>
              <if test="endTime != null">AND occurred_at &lt;= #{endTime}</if>
            </where>
            </script>
            """)
    long countFiltered(
            @Param("userId") Long userId,
            @Param("user") String user,
            @Param("module") String module,
            @Param("result") String result,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime
    );

    @Select("""
            SELECT *
            FROM operation_log
            ORDER BY occurred_at DESC, id DESC
            LIMIT #{limit}
            """)
    List<OperationLog> selectRecent(@Param("limit") int limit);

    @Select("""
            SELECT count(*)
            FROM operation_log
            WHERE occurred_at >= #{since}
            """)
    long countSince(@Param("since") OffsetDateTime since);
}

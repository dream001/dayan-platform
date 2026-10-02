package com.dayan.platform.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dayan.platform.model.AiModel;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface AiModelMapper extends BaseMapper<AiModel> {

    @Select("""
            <script>
            SELECT *
            FROM ai_model
            <where>
              <if test="keyword != null and keyword != ''">
                AND (
                  manufacturer ILIKE '%' || #{keyword} || '%'
                  OR name ILIKE '%' || #{keyword} || '%'
                )
              </if>
              <if test="modelType != null and modelType != ''">
                AND model_type = #{modelType}
              </if>
              <if test="enabled != null">AND enabled = #{enabled}</if>
            </where>
            ORDER BY updated_at DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<AiModel> selectPage(
            @Param("keyword") String keyword,
            @Param("modelType") String modelType,
            @Param("enabled") Boolean enabled,
            @Param("offset") long offset,
            @Param("limit") int limit
    );

    @Select("""
            <script>
            SELECT count(*)
            FROM ai_model
            <where>
              <if test="keyword != null and keyword != ''">
                AND (
                  manufacturer ILIKE '%' || #{keyword} || '%'
                  OR name ILIKE '%' || #{keyword} || '%'
                )
              </if>
              <if test="modelType != null and modelType != ''">
                AND model_type = #{modelType}
              </if>
              <if test="enabled != null">AND enabled = #{enabled}</if>
            </where>
            </script>
            """)
    long countPage(
            @Param("keyword") String keyword,
            @Param("modelType") String modelType,
            @Param("enabled") Boolean enabled
    );
}

package com.dayan.platform.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dayan.platform.model.AiAgent;
import com.dayan.platform.vo.AiAgentViews.AgentView;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface AiAgentMapper extends BaseMapper<AiAgent> {

    @Select("""
            SELECT a.id,
                   a.name,
                   a.description,
                   a.system_prompt,
                   a.model_id,
                   m.name AS model_name,
                   m.manufacturer AS model_manufacturer,
                   m.model_type,
                   a.temperature,
                   a.max_tokens,
                   a.enabled,
                   a.created_at,
                   a.updated_at
            FROM ai_agent a
            JOIN ai_model m ON m.id = a.model_id
            ORDER BY a.updated_at DESC, a.id DESC
            """)
    List<AgentView> selectAllViews();

    @Select("""
            SELECT a.id,
                   a.name,
                   a.description,
                   a.system_prompt,
                   a.model_id,
                   m.name AS model_name,
                   m.manufacturer AS model_manufacturer,
                   m.model_type,
                   a.temperature,
                   a.max_tokens,
                   a.enabled,
                   a.created_at,
                   a.updated_at
            FROM ai_agent a
            JOIN ai_model m ON m.id = a.model_id
            WHERE a.id = #{id}
            """)
    AgentView selectViewById(@Param("id") long id);
}

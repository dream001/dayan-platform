package com.dayan.platform.service;

import com.dayan.platform.dto.AiAgentDtos.AgentRequest;
import com.dayan.platform.vo.AiAgentViews.AgentDebugResult;
import com.dayan.platform.vo.AiAgentViews.AgentView;
import java.util.List;

public interface AiAgentService {

    List<AgentView> list();

    AgentView get(long id);

    AgentView create(AgentRequest request);

    AgentView update(long id, AgentRequest request);

    AgentView changeStatus(long id, boolean enabled);

    void delete(long id);

    AgentDebugResult debug(long id, String message);
}

package com.dayan.platform.controller;

import com.dayan.platform.audit.Audited;
import com.dayan.platform.dto.AiAgentDtos.AgentDebugRequest;
import com.dayan.platform.dto.AiAgentDtos.AgentRequest;
import com.dayan.platform.dto.AiAgentDtos.AgentStatusRequest;
import com.dayan.platform.service.AiAgentService;
import com.dayan.platform.vo.AiAgentViews.AgentDebugResult;
import com.dayan.platform.vo.AiAgentViews.AgentView;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${app.api.base-path}/basic/agents")
public class AiAgentController {

    private final AiAgentService agentService;

    public AiAgentController(AiAgentService agentService) {
        this.agentService = agentService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('basic:agent:view')")
    public List<AgentView> list() {
        return agentService.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('basic:agent:view')")
    public AgentView get(@PathVariable long id) {
        return agentService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('basic:agent:create')")
    @Audited(
            module = "AGENT",
            action = "CREATE",
            targetType = "AI_AGENT",
            targetId = "#result == null ? #request.name() : #result.id()"
    )
    public AgentView create(@Valid @RequestBody AgentRequest request) {
        return agentService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('basic:agent:update')")
    @Audited(module = "AGENT", action = "UPDATE", targetType = "AI_AGENT", targetId = "#id")
    public AgentView update(@PathVariable long id, @Valid @RequestBody AgentRequest request) {
        return agentService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('basic:agent:update')")
    @Audited(module = "AGENT", action = "CHANGE_STATUS", targetType = "AI_AGENT", targetId = "#id")
    public AgentView changeStatus(
            @PathVariable long id,
            @Valid @RequestBody AgentStatusRequest request
    ) {
        return agentService.changeStatus(id, request.enabled());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('basic:agent:delete')")
    @Audited(module = "AGENT", action = "DELETE", targetType = "AI_AGENT", targetId = "#id")
    public void delete(@PathVariable long id) {
        agentService.delete(id);
    }

    @PostMapping("/{id}/debug")
    @PreAuthorize("hasAuthority('basic:agent:debug')")
    @Audited(module = "AGENT", action = "DEBUG", targetType = "AI_AGENT", targetId = "#id")
    public AgentDebugResult debug(
            @PathVariable long id,
            @Valid @RequestBody AgentDebugRequest request
    ) {
        return agentService.debug(id, request.message());
    }
}

package com.dayan.platform.service;

import com.dayan.platform.dto.WorkflowDtos.ActionRuleRequest;
import com.dayan.platform.dto.WorkflowDtos.DefinitionRequest;
import com.dayan.platform.dto.WorkflowDtos.MatchRuleRequest;
import com.dayan.platform.dto.WorkflowDtos.TestRequest;
import com.dayan.platform.vo.WorkflowViews.ActionRuleView;
import com.dayan.platform.vo.WorkflowViews.DatasetOption;
import com.dayan.platform.vo.WorkflowViews.DefinitionView;
import com.dayan.platform.vo.WorkflowViews.MatchRuleView;
import com.dayan.platform.vo.WorkflowViews.Overview;
import com.dayan.platform.vo.WorkflowViews.ProjectOption;
import com.dayan.platform.vo.WorkflowViews.RunView;
import com.dayan.platform.vo.WorkflowViews.TestResult;
import java.util.List;

public interface WorkflowService {

    Overview overview(long userId, boolean admin);

    List<MatchRuleView> matchRules(
            Long projectId,
            Boolean enabled,
            String keyword,
            long userId,
            boolean admin
    );

    MatchRuleView saveMatchRule(
            Long id,
            MatchRuleRequest request,
            long userId,
            boolean admin
    );

    TestResult testMatchRule(long id, TestRequest request, long userId, boolean admin);

    List<ActionRuleView> actionRules(
            Long projectId,
            Boolean enabled,
            String keyword,
            long userId,
            boolean admin
    );

    ActionRuleView saveActionRule(
            Long id,
            ActionRuleRequest request,
            long userId,
            boolean admin
    );

    List<DefinitionView> definitions(
            Long projectId,
            Boolean enabled,
            String keyword,
            long userId,
            boolean admin
    );

    DefinitionView saveDefinition(
            Long id,
            DefinitionRequest request,
            long userId,
            boolean admin
    );

    void delete(String resource, long id, long userId, boolean admin);

    List<ProjectOption> projects(long userId, boolean admin);

    List<DatasetOption> datasets(Long projectId, long userId, boolean admin);

    List<DatasetOption> matchingDatasets(long workflowId, long userId, boolean admin);

    List<RunView> runs(Long projectId, String status, long userId, boolean admin);

    RunView startRun(long workflowId, long datasetId, long userId, boolean admin);
}

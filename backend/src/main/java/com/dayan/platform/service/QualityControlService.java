package com.dayan.platform.service;

import com.dayan.platform.dto.QualityControlDtos.LogFilter;
import com.dayan.platform.dto.QualityControlDtos.OverrideRequest;
import com.dayan.platform.dto.QualityControlDtos.ReportRequest;
import com.dayan.platform.dto.QualityControlDtos.RuleRequest;
import com.dayan.platform.vo.PageResponse;
import com.dayan.platform.vo.QualityControlViews.DatasetOption;
import com.dayan.platform.vo.QualityControlViews.ExecutionDetail;
import com.dayan.platform.vo.QualityControlViews.ExecutionView;
import com.dayan.platform.vo.QualityControlViews.Overview;
import com.dayan.platform.vo.QualityControlViews.ProjectOption;
import com.dayan.platform.vo.QualityControlViews.RuleView;
import com.dayan.platform.vo.QualityControlViews.RunResult;
import java.util.List;
import java.util.Set;

public interface QualityControlService {

    PageResponse<RuleView> rules(
            int page,
            int size,
            Long projectId,
            Boolean enabled,
            String keyword,
            long userId,
            boolean admin
    );

    RuleView createRule(RuleRequest request, long userId, boolean admin);

    RuleView updateRule(long id, RuleRequest request, long userId, boolean admin);

    void deleteRule(long id, long userId, boolean admin);

    List<ProjectOption> projectOptions(long userId, boolean admin);

    List<DatasetOption> datasetOptions(long userId, boolean admin);

    RunResult run(long datasetId, Set<Long> ruleIds, long userId, boolean admin);

    PageResponse<ExecutionView> logs(
            int page,
            int size,
            LogFilter filter,
            long userId,
            boolean admin
    );

    ExecutionDetail execution(long id, long userId, boolean admin);

    ExecutionDetail complete(long id, ReportRequest report, long userId, boolean admin);

    ExecutionView override(
            long id,
            OverrideRequest request,
            long userId,
            boolean admin
    );

    Overview overview(long userId, boolean admin);
}

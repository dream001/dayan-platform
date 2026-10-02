package com.dayan.platform.service.impl;

import com.dayan.platform.model.OperationLog;
import com.dayan.platform.repository.mapper.DashboardMapper;
import com.dayan.platform.repository.mapper.OperationLogMapper;
import com.dayan.platform.repository.query.DashboardStatsRow;
import com.dayan.platform.service.DashboardService;
import com.dayan.platform.vo.AuditViews.OperationLogSummary;
import com.dayan.platform.vo.DashboardView;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardServiceImpl implements DashboardService {

    private static final int RECENT_OPERATION_LIMIT = 10;

    private final DashboardMapper dashboardMapper;
    private final OperationLogMapper operationLogMapper;

    public DashboardServiceImpl(
            DashboardMapper dashboardMapper,
            OperationLogMapper operationLogMapper
    ) {
        this.dashboardMapper = dashboardMapper;
        this.operationLogMapper = operationLogMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardView statistics() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        DashboardStatsRow statistics = dashboardMapper.selectStatistics();
        var recentOperations = operationLogMapper.selectRecent(RECENT_OPERATION_LIMIT).stream()
                .map(this::summary)
                .toList();
        return new DashboardView(
                statistics.getTotalUsers(),
                statistics.getEnabledUsers(),
                statistics.getTotalFiles(),
                statistics.getTotalFileSizeBytes(),
                operationLogMapper.countSince(now.minusDays(7)),
                recentOperations,
                now
        );
    }

    private OperationLogSummary summary(OperationLog log) {
        return new OperationLogSummary(
                log.getId(),
                log.getOperatorId(),
                log.getOperatorName(),
                log.getModule(),
                log.getAction(),
                log.getTargetType(),
                log.getTargetId(),
                log.getResult(),
                log.getErrorSummary(),
                log.getRequestId(),
                log.getOccurredAt(),
                log.getDurationMs()
        );
    }
}

package com.dayan.platform.service.impl;

import com.dayan.platform.model.OperationLog;
import com.dayan.platform.repository.mapper.DashboardMapper;
import com.dayan.platform.repository.mapper.OperationLogMapper;
import com.dayan.platform.repository.query.DashboardRows.BusinessSummaryRow;
import com.dayan.platform.repository.query.DashboardRows.NamedCountRow;
import com.dayan.platform.repository.query.DashboardRows.TrendRow;
import com.dayan.platform.repository.query.DashboardStatsRow;
import com.dayan.platform.service.DashboardService;
import com.dayan.platform.service.DashboardService.DashboardAccess;
import com.dayan.platform.vo.AuditViews.OperationLogSummary;
import com.dayan.platform.vo.DashboardView;
import com.dayan.platform.vo.DashboardView.NamedValue;
import com.dayan.platform.vo.DashboardView.TrendPoint;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    public DashboardView statistics(long userId, DashboardAccess access) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        DashboardStatsRow statistics = dashboardMapper.selectStatistics();
        var recentOperations = operationLogMapper.selectRecent(RECENT_OPERATION_LIMIT).stream()
                .map(this::summary)
                .toList();
        BusinessSummaryRow business = access.dataVisible()
                ? dashboardMapper.selectBusinessSummary(userId, access.platformAdmin())
                : null;
        boolean qualityAvailable = access.dataVisible() && access.qualityVisible();
        return new DashboardView(
                statistics.getTotalUsers(),
                statistics.getEnabledUsers(),
                statistics.getTotalFiles(),
                statistics.getTotalFileSizeBytes(),
                operationLogMapper.countSince(now.minusDays(7)),
                recentOperations,
                access.dataVisible(),
                access.projectsVisible(),
                access.collectionTasksVisible(),
                qualityAvailable,
                business == null ? null : business.datasetCount,
                business == null ? null : business.datasetDurationSeconds,
                business == null ? null : business.annotationCount,
                business == null ? null : business.annotationDurationSeconds,
                business == null ? null : percentage(
                        business.qualifiedAnnotationCount,
                        business.reviewedAnnotationCount
                ),
                business == null ? null : percentage(
                        business.correctedAnnotationCount,
                        business.errorAnnotationCount
                ),
                access.projectsVisible()
                        ? named(dashboardMapper.selectProjectDistribution(
                                userId,
                                access.platformAdmin()
                        ))
                        : List.of(),
                access.collectionTasksVisible()
                        ? named(dashboardMapper.selectCollectionDistribution(
                                userId,
                                access.platformAdmin(),
                                access.collectionTasksWideScope()
                        ))
                        : List.of(),
                access.dataVisible()
                        ? named(dashboardMapper.selectAnnotationQualityDistribution(
                                userId,
                                access.platformAdmin()
                        ))
                        : List.of(),
                qualityAvailable
                        ? named(dashboardMapper.selectDataQualityDistribution(
                                userId,
                                access.platformAdmin()
                        ))
                        : List.of(),
                access.dataVisible()
                        ? singleTrend(dashboardMapper.selectDataGrowthTrend(
                                userId,
                                access.platformAdmin()
                        ))
                        : List.of(),
                qualityAvailable
                        ? tripleTrend(
                                dashboardMapper.selectDataQualityTrend(
                                        userId,
                                        access.platformAdmin()
                                ),
                                "passed",
                                "failed",
                                "unchecked"
                        )
                        : List.of(),
                access.dataVisible()
                        ? singleTrend(dashboardMapper.selectAnnotationGrowthTrend(
                                userId,
                                access.platformAdmin()
                        ))
                        : List.of(),
                access.dataVisible()
                        ? tripleTrend(
                                dashboardMapper.selectAnnotationQualityTrend(
                                        userId,
                                        access.platformAdmin()
                                ),
                                "valid",
                                "error",
                                "invalid"
                        )
                        : List.of(),
                now
        );
    }

    private List<NamedValue> named(List<NamedCountRow> rows) {
        return rows.stream()
                .map(row -> new NamedValue(row.name, row.count))
                .toList();
    }

    private List<TrendPoint> singleTrend(List<TrendRow> rows) {
        return rows.stream()
                .map(row -> new TrendPoint(row.date, Map.of("total", row.firstCount)))
                .toList();
    }

    private List<TrendPoint> tripleTrend(
            List<TrendRow> rows,
            String first,
            String second,
            String third
    ) {
        return rows.stream().map(row -> {
            Map<String, Long> values = new LinkedHashMap<>();
            values.put(first, row.firstCount);
            values.put(second, row.secondCount);
            values.put(third, row.thirdCount);
            return new TrendPoint(row.date, values);
        }).toList();
    }

    private BigDecimal percentage(long numerator, long denominator) {
        if (denominator == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP);
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

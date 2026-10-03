package com.dayan.platform.vo;

import com.dayan.platform.vo.AuditViews.OperationLogSummary;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record DashboardView(
        long totalUsers,
        long enabledUsers,
        long totalFiles,
        long totalFileSizeBytes,
        long recentOperationCount,
        List<OperationLogSummary> recentOperations,
        boolean dataMetricsAvailable,
        boolean projectDistributionAvailable,
        boolean collectionDistributionAvailable,
        boolean qualityDistributionAvailable,
        Long datasetCount,
        BigDecimal datasetDurationSeconds,
        Long annotationCount,
        BigDecimal annotationDurationSeconds,
        BigDecimal annotationPassRate,
        BigDecimal annotationResolveRate,
        List<NamedValue> projectDistribution,
        List<NamedValue> collectionStatusDistribution,
        List<NamedValue> annotationQualityDistribution,
        List<NamedValue> dataQualityDistribution,
        List<TrendPoint> dataGrowthTrend,
        List<TrendPoint> dataQualityTrend,
        List<TrendPoint> annotationGrowthTrend,
        List<TrendPoint> annotationQualityTrend,
        OffsetDateTime generatedAt
) {
    public DashboardView {
        recentOperations = List.copyOf(recentOperations);
        projectDistribution = List.copyOf(projectDistribution);
        collectionStatusDistribution = List.copyOf(collectionStatusDistribution);
        annotationQualityDistribution = List.copyOf(annotationQualityDistribution);
        dataQualityDistribution = List.copyOf(dataQualityDistribution);
        dataGrowthTrend = List.copyOf(dataGrowthTrend);
        dataQualityTrend = List.copyOf(dataQualityTrend);
        annotationGrowthTrend = List.copyOf(annotationGrowthTrend);
        annotationQualityTrend = List.copyOf(annotationQualityTrend);
    }

    public record NamedValue(String name, long value) {
    }

    public record TrendPoint(LocalDate date, Map<String, Long> values) {
        public TrendPoint {
            values = Map.copyOf(values);
        }
    }
}

package com.dayan.platform.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class ChartViews {

    private ChartViews() {
    }

    public record ProjectOption(long id, String code, String name) {
    }

    public record HierarchyNode(String name, long value, List<HierarchyNode> children) {
    }

    public record GraphLink(String source, String target, long value) {
    }

    public record GraphData(List<String> nodes, List<GraphLink> links) {
    }

    public record DurationPoint(String action, BigDecimal averageSeconds, long markerCount) {
    }

    public record CalendarPoint(LocalDate date, long value, BigDecimal deviation) {
    }

    public record CalendarData(
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal dailyAverage,
            List<CalendarPoint> points
    ) {
    }
}

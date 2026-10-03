package com.dayan.platform.service.impl;

import com.dayan.platform.common.api.ErrorCode;
import com.dayan.platform.common.exception.BusinessException;
import com.dayan.platform.repository.mapper.ChartMapper;
import com.dayan.platform.repository.query.ChartQueryRows.CalendarRow;
import com.dayan.platform.repository.query.ChartQueryRows.LinkRow;
import com.dayan.platform.repository.query.ChartQueryRows.RelationshipRow;
import com.dayan.platform.service.ChartService;
import com.dayan.platform.vo.ChartViews.CalendarData;
import com.dayan.platform.vo.ChartViews.CalendarPoint;
import com.dayan.platform.vo.ChartViews.DurationPoint;
import com.dayan.platform.vo.ChartViews.GraphData;
import com.dayan.platform.vo.ChartViews.GraphLink;
import com.dayan.platform.vo.ChartViews.HierarchyNode;
import com.dayan.platform.vo.ChartViews.ProjectOption;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChartServiceImpl implements ChartService {

    private final ChartMapper chartMapper;

    public ChartServiceImpl(ChartMapper chartMapper) {
        this.chartMapper = chartMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectOption> projectOptions(long userId, boolean platformAdmin) {
        return chartMapper.selectProjectOptions(userId, platformAdmin);
    }

    @Override
    @Transactional(readOnly = true)
    public List<HierarchyNode> relationships(
            long projectId,
            String locale,
            long userId,
            boolean platformAdmin
    ) {
        requireProject(projectId, userId, platformAdmin);
        List<RelationshipRow> rows = chartMapper.selectRelationships(
                projectId,
                locale != null && locale.toLowerCase().startsWith("zh")
        );
        Map<String, Map<String, Map<String, Long>>> hierarchy = new LinkedHashMap<>();
        for (RelationshipRow row : rows) {
            String objectB = hasText(row.objectBName) ? row.objectBName : "-";
            hierarchy
                    .computeIfAbsent(row.skillName, key -> new LinkedHashMap<>())
                    .computeIfAbsent(row.objectAName, key -> new LinkedHashMap<>())
                    .merge(objectB, row.markerCount, Long::sum);
        }

        return hierarchy.entrySet().stream().map(skill -> {
            List<HierarchyNode> objectNodes = skill.getValue().entrySet().stream().map(objectA -> {
                List<HierarchyNode> leaves = objectA.getValue().entrySet().stream()
                        .map(entry -> new HierarchyNode(entry.getKey(), entry.getValue(), List.of()))
                        .toList();
                return new HierarchyNode(
                        objectA.getKey(),
                        leaves.stream().mapToLong(HierarchyNode::value).sum(),
                        leaves
                );
            }).toList();
            return new HierarchyNode(
                    skill.getKey(),
                    objectNodes.stream().mapToLong(HierarchyNode::value).sum(),
                    objectNodes
            );
        }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public GraphData planning(
            long projectId,
            String locale,
            long userId,
            boolean platformAdmin
    ) {
        requireProject(projectId, userId, platformAdmin);
        return graph(chartMapper.selectPlanningLinks(
                projectId,
                locale != null && locale.toLowerCase().startsWith("zh")
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DurationPoint> durations(
            long projectId,
            long userId,
            boolean platformAdmin
    ) {
        requireProject(projectId, userId, platformAdmin);
        return chartMapper.selectDurations(projectId).stream()
                .map(row -> new DurationPoint(
                        row.actionName,
                        row.averageSeconds,
                        row.markerCount
                ))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public GraphData dependencies(long projectId, long userId, boolean platformAdmin) {
        requireProject(projectId, userId, platformAdmin);
        return graph(chartMapper.selectDependencyLinks(projectId));
    }

    @Override
    @Transactional(readOnly = true)
    public CalendarData calendar(long projectId, long userId, boolean platformAdmin) {
        requireProject(projectId, userId, platformAdmin);
        List<CalendarRow> rows = chartMapper.selectCalendar(projectId);
        if (rows.isEmpty()) {
            return new CalendarData(null, null, BigDecimal.ZERO, List.of());
        }
        long total = rows.stream().mapToLong(row -> row.markerCount).sum();
        BigDecimal average = BigDecimal.valueOf(total)
                .divide(BigDecimal.valueOf(rows.size()), 2, RoundingMode.HALF_UP);
        List<CalendarPoint> points = rows.stream()
                .map(row -> new CalendarPoint(
                        row.annotationDate,
                        row.markerCount,
                        BigDecimal.valueOf(row.markerCount).subtract(average)
                ))
                .toList();
        LocalDate startDate = rows.get(0).annotationDate;
        LocalDate endDate = rows.get(rows.size() - 1).annotationDate;
        return new CalendarData(startDate, endDate, average, points);
    }

    private GraphData graph(List<LinkRow> rows) {
        LinkedHashSet<String> nodes = new LinkedHashSet<>();
        List<GraphLink> links = new ArrayList<>();
        for (LinkRow row : rows) {
            nodes.add(row.source);
            nodes.add(row.target);
            links.add(new GraphLink(row.source, row.target, row.linkCount));
        }
        return new GraphData(List.copyOf(nodes), links);
    }

    private void requireProject(long projectId, long userId, boolean platformAdmin) {
        if (chartMapper.countAccessibleProject(projectId, userId, platformAdmin) == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Project access denied");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

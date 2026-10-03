package com.dayan.platform.service;

import com.dayan.platform.vo.ChartViews.CalendarData;
import com.dayan.platform.vo.ChartViews.DurationPoint;
import com.dayan.platform.vo.ChartViews.GraphData;
import com.dayan.platform.vo.ChartViews.HierarchyNode;
import com.dayan.platform.vo.ChartViews.ProjectOption;
import java.util.List;

public interface ChartService {

    List<ProjectOption> projectOptions(long userId, boolean platformAdmin);

    List<HierarchyNode> relationships(
            long projectId,
            String locale,
            long userId,
            boolean platformAdmin
    );

    GraphData planning(
            long projectId,
            String locale,
            long userId,
            boolean platformAdmin
    );

    List<DurationPoint> durations(long projectId, long userId, boolean platformAdmin);

    GraphData dependencies(long projectId, long userId, boolean platformAdmin);

    CalendarData calendar(long projectId, long userId, boolean platformAdmin);
}

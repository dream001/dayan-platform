package com.dayan.platform.service;

import com.dayan.platform.vo.DashboardView;

public interface DashboardService {

    DashboardView statistics(long userId, DashboardAccess access);

    record DashboardAccess(
            boolean platformAdmin,
            boolean dataVisible,
            boolean projectsVisible,
            boolean collectionTasksVisible,
            boolean collectionTasksWideScope,
            boolean qualityVisible
    ) {
    }
}

package com.dayan.platform.service;

import com.dayan.platform.dto.RobotDtos.RobotRequest;
import com.dayan.platform.dto.RobotDtos.RobotType;
import com.dayan.platform.vo.RobotViews.RobotDataset;
import com.dayan.platform.vo.RobotViews.RobotSummary;
import java.util.List;

public interface RobotService {

    List<RobotSummary> list(RobotType robotType, long userId, boolean admin);

    RobotSummary create(RobotRequest request, long userId, boolean admin);

    RobotSummary update(long id, RobotRequest request, long userId, boolean admin);

    void delete(long id, boolean admin);

    List<RobotDataset> datasets(long id, long userId, boolean admin);
}

package com.dayan.platform.service;

import com.dayan.platform.dto.CollectionTaskDtos.CollectionStatusRequest;
import com.dayan.platform.dto.CollectionTaskDtos.CollectionTaskRequest;
import com.dayan.platform.vo.CollectionTaskViews.CollectionOptions;
import com.dayan.platform.vo.CollectionTaskViews.CollectionStatusCounts;
import com.dayan.platform.vo.CollectionTaskViews.CollectionTaskDetail;
import com.dayan.platform.vo.CollectionTaskViews.CollectionTaskSummary;
import com.dayan.platform.vo.PageResponse;

public interface CollectionTaskService {

    record Access(
            long userId,
            boolean administrator,
            boolean manage,
            boolean review,
            boolean submit,
            boolean unlinkData
    ) {
    }

    PageResponse<CollectionTaskSummary> page(
            int page,
            int size,
            String keyword,
            Long collectorId,
            String status,
            Access access
    );

    CollectionStatusCounts statusCounts(Access access);

    CollectionTaskDetail detail(long id, Access access);

    CollectionTaskDetail create(CollectionTaskRequest request, Access access);

    CollectionTaskDetail update(long id, CollectionTaskRequest request, Access access);

    CollectionTaskDetail changeStatus(long id, CollectionStatusRequest request, Access access);

    void delete(long id, Access access);

    CollectionOptions options(Long projectId, Access access);

    void linkDataset(long id, long fileId, Access access);

    void unlinkDataset(long id, long fileId, Access access);
}

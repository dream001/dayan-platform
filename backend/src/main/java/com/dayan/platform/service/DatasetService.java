package com.dayan.platform.service;

import com.dayan.platform.dto.DatasetDtos.AnnotateRequest;
import com.dayan.platform.dto.DatasetDtos.DatasetRegisterRequest;
import com.dayan.platform.dto.DatasetDtos.ImportRequest;
import com.dayan.platform.dto.DatasetDtos.RenameItem;
import com.dayan.platform.dto.DatasetDtos.RobotRequest;
import com.dayan.platform.dto.DatasetDtos.TagBatchRequest;
import com.dayan.platform.dto.DatasetFilter;
import com.dayan.platform.repository.query.OptionRow;
import com.dayan.platform.vo.DatasetViews.DatasetDetail;
import com.dayan.platform.vo.DatasetViews.DatasetStats;
import com.dayan.platform.vo.DatasetViews.DatasetView;
import com.dayan.platform.vo.DatasetViews.ItemResult;
import com.dayan.platform.vo.DatasetViews.TaskBrief;
import com.dayan.platform.vo.PageResponse;
import java.util.List;

public interface DatasetService {

    PageResponse<DatasetView> page(DatasetFilter filter);

    long storageTotal(DatasetFilter filter);

    DatasetDetail detail(long id, long userId, boolean admin);

    DatasetView register(DatasetRegisterRequest request, long userId);

    List<ItemResult> rename(List<RenameItem> items, long userId, boolean admin);

    TaskBrief annotate(AnnotateRequest request, long userId, boolean admin);

    void addTags(TagBatchRequest request, long userId, boolean admin);

    void removeTags(TagBatchRequest request, long userId, boolean admin);

    List<ItemResult> refreshMetadata(List<Long> ids, long userId, boolean admin);

    void softDelete(List<Long> ids, long userId, boolean admin);

    void importToProject(ImportRequest request, long userId, boolean admin);

    void assignRobot(RobotRequest request, long userId, boolean admin);

    DatasetStats stats(List<Long> ids, long userId, boolean admin);

    List<DatasetView> trash(long userId, boolean admin);

    void restore(long id, long userId, boolean admin);

    List<String> robotOptions();

    List<String> tagOptions();

    List<OptionRow> userOptions();
}

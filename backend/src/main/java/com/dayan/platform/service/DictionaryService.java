package com.dayan.platform.service;

import com.dayan.platform.dto.DictionaryDtos.DictionaryBatchRequest;
import com.dayan.platform.dto.DictionaryDtos.DictionaryRequest;
import com.dayan.platform.dto.DictionaryDtos.DictionaryScope;
import com.dayan.platform.dto.DictionaryDtos.DictionarySort;
import com.dayan.platform.dto.DictionaryDtos.DictionaryType;
import com.dayan.platform.dto.DictionaryDtos.SortDirection;
import com.dayan.platform.vo.DictionaryViews.DictionaryBatchResult;
import com.dayan.platform.vo.DictionaryViews.DictionaryExport;
import com.dayan.platform.vo.DictionaryViews.DictionaryItemView;
import com.dayan.platform.vo.DictionaryViews.DictionaryOverview;
import com.dayan.platform.vo.DictionaryViews.DictionaryProjectOption;
import com.dayan.platform.vo.PageResponse;
import java.util.List;
import java.util.Set;

public interface DictionaryService {

    PageResponse<DictionaryItemView> page(
            int page,
            int size,
            DictionaryType type,
            String keyword,
            DictionaryScope scope,
            Long projectId,
            DictionarySort sort,
            SortDirection direction,
            long userId,
            boolean platformAdmin
    );

    DictionaryOverview overview(long userId, boolean platformAdmin);

    List<DictionaryProjectOption> projectOptions(long userId, boolean platformAdmin);

    DictionaryItemView create(
            DictionaryRequest request,
            long userId,
            boolean platformAdmin
    );

    DictionaryBatchResult batchCreate(
            DictionaryBatchRequest request,
            long userId,
            boolean platformAdmin
    );

    DictionaryItemView update(
            long id,
            DictionaryRequest request,
            long userId,
            boolean platformAdmin
    );

    void delete(long id, long userId, boolean platformAdmin);

    DictionaryBatchResult batchDelete(Set<Long> ids, long userId, boolean platformAdmin);

    DictionaryExport export(DictionaryType type, long userId, boolean platformAdmin);
}

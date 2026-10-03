package com.dayan.platform.service;

import com.dayan.platform.dto.AiModelDtos.ModelRequest;
import com.dayan.platform.dto.AiModelDtos.ModelType;
import com.dayan.platform.vo.AiModelViews.ModelSummary;
import com.dayan.platform.vo.AiModelViews.ModelDebugResult;
import com.dayan.platform.vo.AiModelViews.ModelTestResult;
import com.dayan.platform.vo.PageResponse;

public interface AiModelService {

    PageResponse<ModelSummary> page(
            int page,
            int size,
            String keyword,
            ModelType modelType,
            Boolean enabled
    );

    ModelSummary get(long id);

    ModelSummary create(ModelRequest request);

    ModelSummary update(long id, ModelRequest request);

    ModelSummary changeStatus(long id, boolean enabled);

    void delete(long id);

    ModelTestResult test(long id);

    ModelDebugResult debug(long id, String input);
}

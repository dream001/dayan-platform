package com.dayan.platform.service;

import com.dayan.platform.dto.CloudStorageDtos.CloudStorageRequest;
import com.dayan.platform.vo.CloudStorageViews.CloudStorageOverview;
import com.dayan.platform.vo.CloudStorageViews.CloudStorageView;
import java.util.List;

public interface CloudStorageService {

    List<CloudStorageView> list();

    CloudStorageOverview overview();

    CloudStorageView create(CloudStorageRequest request, long userId);

    CloudStorageView update(long id, CloudStorageRequest request);

    CloudStorageView test(long id);

    CloudStorageView setDefault(long id);

    void delete(long id);
}

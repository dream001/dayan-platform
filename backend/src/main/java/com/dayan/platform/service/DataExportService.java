package com.dayan.platform.service;

import com.dayan.platform.dto.DataExportDtos.CreateRequest;
import com.dayan.platform.vo.DataExportViews.DatasetOption;
import com.dayan.platform.vo.DataExportViews.QuotaUserView;
import com.dayan.platform.vo.DataExportViews.QuotaView;
import com.dayan.platform.vo.DataExportViews.TaskDetail;
import com.dayan.platform.vo.DataExportViews.TaskView;
import com.dayan.platform.vo.PageResponse;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.List;

public interface DataExportService {

    List<DatasetOption> options(
            Long projectId,
            Long collectorId,
            OffsetDateTime from,
            OffsetDateTime to,
            String keyword,
            long userId,
            boolean admin
    );

    TaskView create(CreateRequest request, long userId, boolean admin);

    PageResponse<TaskView> page(
            int page,
            int size,
            String format,
            String status,
            String keyword,
            long userId,
            boolean admin
    );

    TaskDetail detail(long id, long userId, boolean admin);

    QuotaView quota(long userId);

    List<QuotaUserView> quotas();

    void updateQuota(long userId, int limit, long updatedBy);

    Download download(long id, long userId, boolean admin);

    record Download(
            String fileName,
            String contentType,
            long sizeBytes,
            InputStream inputStream
    ) {
    }
}

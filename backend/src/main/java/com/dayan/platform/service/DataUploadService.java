package com.dayan.platform.service;

import com.dayan.platform.dto.DataUploadDtos.CreateSessionRequest;
import com.dayan.platform.vo.DataUploadViews.DatasetView;
import com.dayan.platform.vo.DataUploadViews.UploadOptions;
import com.dayan.platform.vo.DataUploadViews.UploadSessionView;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface DataUploadService {

    UploadOptions options(long userId);

    DatasetView uploadDirect(
            long projectId,
            String storageKey,
            String dataType,
            String sourceFingerprint,
            String robotType,
            MultipartFile file,
            long userId
    );

    UploadSessionView createSession(CreateSessionRequest request, long userId);

    UploadSessionView session(UUID id, long userId);

    void uploadPart(UUID id, int partNumber, MultipartFile chunk, long userId);

    void pause(UUID id, long userId);

    UploadSessionView resume(UUID id, long userId);

    DatasetView complete(UUID id, long userId);

    void cancel(UUID id, long userId);
}

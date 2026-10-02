package com.dayan.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;

import com.dayan.platform.common.exception.BusinessException;
import com.dayan.platform.model.StoredFile;
import com.dayan.platform.repository.mapper.StoredFileMapper;
import com.dayan.platform.repository.storage.ObjectStorage;
import com.dayan.platform.repository.storage.ObjectStorageException;
import com.dayan.platform.support.PostgreSqlIntegrationTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class FileDeletionRecoveryIntegrationTest extends PostgreSqlIntegrationTestSupport {

    @Autowired
    private FileService fileService;
    @Autowired
    private StoredFileMapper storedFileMapper;
    @MockitoBean
    private ObjectStorage objectStorage;

    @Test
    void recordsFailedDeletionAndAllowsIdempotentRecovery() {
        StoredFile file = new StoredFile();
        file.setBucketName(MINIO_BUCKET);
        file.setObjectKey("recovery/" + UUID.randomUUID());
        file.setOriginalName("recovery.txt");
        file.setContentType("text/plain");
        file.setSizeBytes(8L);
        file.setStatus("READY");
        storedFileMapper.insert(file);

        doThrow(new ObjectStorageException("injected MinIO outage", new IOExceptionStub()))
                .doNothing()
                .when(objectStorage)
                .remove(file.getObjectKey());

        assertThatThrownBy(() -> fileService.delete(file.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("File storage operation failed");
        assertThat(storedFileMapper.selectById(file.getId()).getStatus()).isEqualTo("FAILED");

        fileService.delete(file.getId());
        assertThat(storedFileMapper.selectById(file.getId())).isNull();
    }

    private static final class IOExceptionStub extends Exception {
    }
}

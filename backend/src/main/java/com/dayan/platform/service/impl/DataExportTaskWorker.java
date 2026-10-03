package com.dayan.platform.service.impl;

import com.dayan.platform.repository.mapper.DataExportMapper;
import com.dayan.platform.repository.query.DataExportRows.DatasetRow;
import com.dayan.platform.repository.query.DataExportRows.TaskRow;
import com.dayan.platform.repository.storage.ObjectStorage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DataExportTaskWorker {

    private static final Logger log = LoggerFactory.getLogger(DataExportTaskWorker.class);

    private final DataExportMapper exportMapper;
    private final DataExportArchiveBuilder archiveBuilder;
    private final ObjectStorage objectStorage;

    public DataExportTaskWorker(
            DataExportMapper exportMapper,
            DataExportArchiveBuilder archiveBuilder,
            ObjectStorage objectStorage
    ) {
        this.exportMapper = exportMapper;
        this.archiveBuilder = archiveBuilder;
        this.objectStorage = objectStorage;
    }

    @Scheduled(fixedDelayString = "${app.export.poll-interval:2000}")
    public void dispatch() {
        for (Long taskId : exportMapper.selectPendingIds()) {
            process(taskId);
        }
    }

    void process(long taskId) {
        if (exportMapper.claim(taskId) != 1) {
            return;
        }
        Path temporaryFile = null;
        try {
            TaskRow task = exportMapper.selectTaskForWorker(taskId);
            List<DatasetRow> datasets = exportMapper.selectTaskDatasets(taskId);
            var annotations = exportMapper.selectTaskAnnotations(taskId);
            var generated = archiveBuilder.build(
                    taskId,
                    task.name,
                    task.format,
                    task.configJson,
                    datasets,
                    annotations,
                    processed -> exportMapper.updateProgress(
                            taskId,
                            processed,
                            Math.min(95, Math.max(1, processed * 90 / datasets.size()))
                    )
            );
            temporaryFile = generated.path();
            long size = Files.size(temporaryFile);
            String objectKey = "exports/%d/%s/%s".formatted(
                    task.creatorId,
                    UUID.randomUUID(),
                    generated.fileName()
            );
            try (var input = Files.newInputStream(temporaryFile)) {
                objectStorage.put(objectKey, input, size, generated.contentType());
            }
            exportMapper.complete(
                    taskId,
                    generated.fileName(),
                    objectKey,
                    generated.contentType(),
                    size
            );
        } catch (Exception exception) {
            log.error("Data export failed: taskId={}", taskId, exception);
            String message = exception.getMessage();
            if (message == null || message.isBlank()) {
                message = exception.getClass().getSimpleName();
            }
            exportMapper.fail(taskId, message.substring(0, Math.min(message.length(), 2000)));
        } finally {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (Exception cleanupFailure) {
                    log.warn("Unable to delete export temporary file: {}", temporaryFile);
                }
            }
        }
    }
}

package com.dayan.platform.service.impl;

import com.dayan.platform.common.api.ErrorCode;
import com.dayan.platform.common.exception.BusinessException;
import com.dayan.platform.dto.DataExportDtos.CreateRequest;
import com.dayan.platform.model.DataExportTask;
import com.dayan.platform.repository.mapper.DataExportMapper;
import com.dayan.platform.repository.query.DataExportRows.DatasetRow;
import com.dayan.platform.repository.query.DataExportRows.QuotaRow;
import com.dayan.platform.repository.query.DataExportRows.TaskRow;
import com.dayan.platform.repository.storage.ObjectStorage;
import com.dayan.platform.repository.storage.ObjectStorageException;
import com.dayan.platform.service.DataExportService;
import com.dayan.platform.vo.DataExportViews.DatasetOption;
import com.dayan.platform.vo.DataExportViews.QuotaUserView;
import com.dayan.platform.vo.DataExportViews.QuotaView;
import com.dayan.platform.vo.DataExportViews.TaskDetail;
import com.dayan.platform.vo.DataExportViews.TaskView;
import com.dayan.platform.vo.PageResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DataExportServiceImpl implements DataExportService {

    private final DataExportMapper exportMapper;
    private final ObjectStorage objectStorage;
    private final ObjectMapper objectMapper;

    public DataExportServiceImpl(
            DataExportMapper exportMapper,
            ObjectStorage objectStorage,
            ObjectMapper objectMapper
    ) {
        this.exportMapper = exportMapper;
        this.objectStorage = objectStorage;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<DatasetOption> options(
            Long projectId,
            Long collectorId,
            OffsetDateTime from,
            OffsetDateTime to,
            String keyword,
            long userId,
            boolean admin
    ) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "开始时间不能晚于结束时间");
        }
        return exportMapper.selectOptions(
                userId,
                admin,
                projectId,
                collectorId,
                from,
                to,
                normalize(keyword)
        ).stream().map(this::toDataset).toList();
    }

    @Override
    @Transactional
    public TaskView create(CreateRequest request, long userId, boolean admin) {
        List<Long> ids = new LinkedHashSet<>(request.datasetIds()).stream().toList();
        List<DatasetRow> datasets = exportMapper.selectExportable(ids, userId, admin);
        if (datasets.size() != ids.size()) {
            throw new BusinessException(
                    ErrorCode.INVALID_ARGUMENT,
                    "所选数据集包含无权访问、未完成标注或文件不可用的数据"
            );
        }
        exportMapper.ensureQuota(userId);
        QuotaRow quota = exportMapper.selectQuotaForUpdate(userId);
        if (ids.size() > Math.max(quota.quotaLimit - quota.used, 0)) {
            throw new BusinessException(ErrorCode.CONFLICT, "导出配额不足");
        }

        DataExportTask task = new DataExportTask();
        task.setName(request.name().trim());
        task.setFormat(request.format().toUpperCase(Locale.ROOT));
        task.setStatus("PENDING");
        task.setProgress(0);
        task.setProcessedCount(0);
        task.setDatasetCount(ids.size());
        task.setConfigJson(configJson(request));
        task.setCreatorId(userId);
        exportMapper.insert(task);
        exportMapper.insertTaskDatasets(task.getId(), ids);
        return requireTask(task.getId(), userId, admin);
    }

    @Override
    public PageResponse<TaskView> page(
            int page,
            int size,
            String format,
            String status,
            String keyword,
            long userId,
            boolean admin
    ) {
        String normalizedFormat = upper(format);
        String normalizedStatus = upper(status);
        long total = exportMapper.countTasks(
                userId,
                admin,
                normalizedFormat,
                normalizedStatus,
                normalize(keyword)
        );
        long offset = (long) (page - 1) * size;
        List<TaskView> items = exportMapper.selectTaskPage(
                userId,
                admin,
                normalizedFormat,
                normalizedStatus,
                normalize(keyword),
                size,
                offset
        ).stream().map(this::toTask).toList();
        return PageResponse.of(page, size, total, items);
    }

    @Override
    public TaskDetail detail(long id, long userId, boolean admin) {
        TaskView task = requireTask(id, userId, admin);
        List<DatasetOption> datasets = exportMapper.selectTaskDatasets(id)
                .stream()
                .map(this::toDataset)
                .toList();
        return new TaskDetail(task, datasets);
    }

    @Override
    public QuotaView quota(long userId) {
        QuotaRow row = exportMapper.selectQuota(userId);
        int limit = row.quotaLimit;
        int used = row.used;
        return new QuotaView(used, limit, Math.max(limit - used, 0));
    }

    @Override
    public List<QuotaUserView> quotas() {
        return exportMapper.selectQuotas().stream()
                .map(row -> new QuotaUserView(
                        row.userId,
                        row.username,
                        row.displayName,
                        row.used,
                        row.quotaLimit
                ))
                .toList();
    }

    @Override
    @Transactional
    public void updateQuota(long userId, int limit, long updatedBy) {
        exportMapper.upsertQuota(userId, limit, updatedBy);
    }

    @Override
    public Download download(long id, long userId, boolean admin) {
        TaskRow task = exportMapper.selectTask(id, userId, admin);
        if (task == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "导出任务不存在");
        }
        if (!"COMPLETED".equals(task.status) || !StringUtils.hasText(task.objectKey)) {
            throw new BusinessException(ErrorCode.CONFLICT, "导出文件尚未生成");
        }
        try {
            return new Download(
                    task.fileName,
                    task.contentType,
                    task.fileSize,
                    objectStorage.get(task.objectKey).inputStream()
            );
        } catch (ObjectStorageException exception) {
            throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR);
        }
    }

    private TaskView requireTask(long id, long userId, boolean admin) {
        TaskRow row = exportMapper.selectTask(id, userId, admin);
        if (row == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "导出任务不存在");
        }
        return toTask(row);
    }

    private String configJson(CreateRequest request) {
        Map<String, Object> config = new java.util.LinkedHashMap<>();
        put(config, "mediaMode", request.mediaMode());
        put(config, "sampleRate", request.sampleRate());
        put(config, "chunkSize", request.chunkSize());
        put(config, "version", request.version());
        put(config, "strictMatch", request.strictMatch());
        put(config, "blurFaces", request.blurFaces());
        put(config, "annotationType", request.annotationType());
        try {
            return objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "导出参数无法序列化");
        }
    }

    private void put(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private TaskView toTask(TaskRow row) {
        return new TaskView(
                row.id,
                row.name,
                row.format,
                row.status,
                row.progress,
                row.processedCount,
                row.datasetCount,
                row.configJson,
                row.fileName,
                row.fileSize,
                row.errorMessage,
                row.creatorId,
                row.creatorName,
                row.startedAt,
                row.completedAt,
                row.createdAt
        );
    }

    private DatasetOption toDataset(DatasetRow row) {
        return new DatasetOption(
                row.id,
                row.name,
                row.dataType,
                row.sizeBytes,
                row.durationSeconds,
                row.projectId,
                row.projectName,
                row.collectorName,
                row.createdAt
        );
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String upper(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }
}

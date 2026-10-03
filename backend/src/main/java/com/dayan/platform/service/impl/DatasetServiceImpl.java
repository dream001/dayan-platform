package com.dayan.platform.service.impl;

import com.dayan.platform.common.api.ErrorCode;
import com.dayan.platform.common.exception.BusinessException;
import com.dayan.platform.config.MinioProperties;
import com.dayan.platform.dto.DatasetDtos.AnnotateRequest;
import com.dayan.platform.dto.DatasetDtos.DatasetRegisterRequest;
import com.dayan.platform.dto.DatasetDtos.ImportRequest;
import com.dayan.platform.dto.DatasetDtos.RenameItem;
import com.dayan.platform.dto.DatasetDtos.RobotRequest;
import com.dayan.platform.dto.DatasetDtos.TagBatchRequest;
import com.dayan.platform.dto.DatasetFilter;
import com.dayan.platform.model.AnnotationTask;
import com.dayan.platform.model.Dataset;
import com.dayan.platform.model.DatasetTag;
import com.dayan.platform.model.StoredFile;
import com.dayan.platform.model.UserAccount;
import com.dayan.platform.repository.mapper.AnnotationTaskMapper;
import com.dayan.platform.repository.mapper.AnnotationTaskWorkflowMapper;
import com.dayan.platform.repository.mapper.DatasetAnnotationMapper;
import com.dayan.platform.repository.mapper.DatasetMapper;
import com.dayan.platform.repository.mapper.DatasetTagMapper;
import com.dayan.platform.repository.mapper.RobotMapper;
import com.dayan.platform.repository.mapper.StoredFileMapper;
import com.dayan.platform.repository.mapper.UserAccountMapper;
import com.dayan.platform.repository.query.AnnotationAggRow;
import com.dayan.platform.repository.query.DatasetListRow;
import com.dayan.platform.repository.query.DatasetSelectionAggRow;
import com.dayan.platform.repository.query.OptionRow;
import com.dayan.platform.repository.query.TaskDetailRow;
import com.dayan.platform.repository.storage.ObjectStorage;
import com.dayan.platform.repository.storage.ObjectStorageException;
import com.dayan.platform.service.DatasetService;
import com.dayan.platform.vo.DatasetViews.DatasetDetail;
import com.dayan.platform.vo.DatasetViews.DatasetStats;
import com.dayan.platform.vo.DatasetViews.DatasetView;
import com.dayan.platform.vo.DatasetViews.ItemResult;
import com.dayan.platform.vo.DatasetViews.Preview;
import com.dayan.platform.vo.DatasetViews.TaskBrief;
import com.dayan.platform.vo.PageResponse;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.http.ContentDisposition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatasetServiceImpl implements DatasetService {

    private static final String READY = "READY";
    private static final String OPEN_SHARED_MESSAGE =
            "所选数据包含开源共享数据，仅可查看/导出，无法进行二次编辑";

    private final DatasetMapper datasetMapper;
    private final DatasetTagMapper datasetTagMapper;
    private final AnnotationTaskMapper annotationTaskMapper;
    private final AnnotationTaskWorkflowMapper annotationTaskWorkflowMapper;
    private final DatasetAnnotationMapper datasetAnnotationMapper;
    private final RobotMapper robotMapper;
    private final StoredFileMapper storedFileMapper;
    private final UserAccountMapper userAccountMapper;
    private final ObjectStorage objectStorage;
    private final MinioProperties minioProperties;

    public DatasetServiceImpl(
            DatasetMapper datasetMapper,
            DatasetTagMapper datasetTagMapper,
            AnnotationTaskMapper annotationTaskMapper,
            AnnotationTaskWorkflowMapper annotationTaskWorkflowMapper,
            DatasetAnnotationMapper datasetAnnotationMapper,
            RobotMapper robotMapper,
            StoredFileMapper storedFileMapper,
            UserAccountMapper userAccountMapper,
            ObjectStorage objectStorage,
            MinioProperties minioProperties
    ) {
        this.datasetMapper = datasetMapper;
        this.datasetTagMapper = datasetTagMapper;
        this.annotationTaskMapper = annotationTaskMapper;
        this.annotationTaskWorkflowMapper = annotationTaskWorkflowMapper;
        this.datasetAnnotationMapper = datasetAnnotationMapper;
        this.robotMapper = robotMapper;
        this.storedFileMapper = storedFileMapper;
        this.userAccountMapper = userAccountMapper;
        this.objectStorage = objectStorage;
        this.minioProperties = minioProperties;
    }

    @Override
    public PageResponse<DatasetView> page(DatasetFilter filter) {
        prepare(filter);
        List<DatasetListRow> rows = datasetMapper.selectFilteredPage(filter);
        long total = datasetMapper.countFiltered(filter);
        Map<Long, List<String>> tagMap = tagMap(rows.stream().map(row -> row.id).toList());
        List<DatasetView> items = rows.stream()
                .map(row -> toView(row, tagMap.getOrDefault(row.id, List.of())))
                .toList();
        return PageResponse.of(filter.getPage(), filter.getSize(), total, items);
    }

    @Override
    public long storageTotal(DatasetFilter filter) {
        prepare(filter);
        return datasetMapper.sumSizeFiltered(filter);
    }

    @Override
    public DatasetDetail detail(long id, long userId, boolean admin) {
        DatasetListRow row = datasetMapper.selectActiveById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Dataset not found");
        }
        if (!admin && row.uploaderId != userId
                && (row.projectId == null
                || datasetMapper.countAccessibleProject(row.projectId, userId) == 0)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        List<String> tags = tagMap(List.of(id)).getOrDefault(id, List.of());
        DatasetView view = toView(row, tags);

        TaskBrief task = null;
        if (row.taskId != null) {
            TaskDetailRow taskRow = annotationTaskMapper.selectTaskRow(row.taskId);
            if (taskRow != null) {
                task = new TaskBrief(
                        taskRow.id,
                        taskRow.name,
                        taskRow.status,
                        taskRow.datasetCount,
                        taskRow.creatorName,
                        taskRow.createdAt
                );
            }
        }

        AnnotationAggRow agg = datasetAnnotationMapper.aggregateForDatasets(List.of(id));
        long invalid = datasetAnnotationMapper.countInvalidForDataset(id);
        var annotation = new com.dayan.platform.vo.DatasetViews.AnnotationSummary(
                agg.totalAnnotations,
                agg.qualifiedAnnotations,
                invalid,
                agg.reviewedCount,
                agg.coveredDuration
        );

        return new DatasetDetail(view, task, annotation, view.preview());
    }

    @Override
    @Transactional
    public DatasetView register(DatasetRegisterRequest request, long userId) {
        StoredFile file = storedFileMapper.selectById(request.fileId());
        if (file == null || !READY.equals(file.getStatus())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "源文件不存在或未就绪");
        }
        String name = request.name().trim();
        String dataType = resolveDataType(request.dataType(), file);

        Dataset active = datasetMapper.selectActiveByName(name);
        if (active != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "同名数据集已存在");
        }

        long datasetId;
        Dataset deleted = datasetMapper.selectDeletedByName(name);
        if (deleted != null && Boolean.TRUE.equals(request.restoreExisting())) {
            restoreExistingDataset(deleted, file, request, dataType);
            datasetId = deleted.getId();
        } else {
            datasetId = createDataset(file, request, userId, name, dataType);
        }
        if (request.tags() != null) {
            linkTags(datasetId, request.tags());
        }
        DatasetListRow row = datasetMapper.selectActiveById(datasetId);
        return toView(row, tagMap(List.of(datasetId)).getOrDefault(datasetId, List.of()));
    }

    @Override
    @Transactional
    public List<ItemResult> rename(List<RenameItem> items, long userId, boolean admin) {
        List<Long> ids = items.stream().map(RenameItem::id).toList();
        guardOpenShared(requireActive(ids, userId, admin));
        List<ItemResult> results = new ArrayList<>();
        for (RenameItem item : items) {
            String newName = item.name().trim();
            Dataset other = datasetMapper.selectActiveByName(newName);
            if (other != null && !other.getId().equals(item.id())) {
                results.add(new ItemResult(item.id(), "FAILED", "名称已被占用"));
                continue;
            }
            Dataset update = new Dataset();
            update.setId(item.id());
            update.setName(newName);
            datasetMapper.updateById(update);
            results.add(new ItemResult(item.id(), READY, null));
        }
        return results;
    }

    @Override
    @Transactional
    public TaskBrief annotate(AnnotateRequest request, long userId, boolean admin) {
        List<Dataset> datasets = requireActive(request.ids(), userId, admin);
        guardOpenShared(datasets);

        AnnotationTask task = new AnnotationTask();
        task.setName(request.name().trim());
        Long projectId = datasets.getFirst().getProjectId();
        if (projectId == null || datasets.stream()
                .anyMatch(dataset -> !Objects.equals(projectId, dataset.getProjectId()))) {
            throw new BusinessException(
                    ErrorCode.INVALID_ARGUMENT,
                    "发起标注的数据集必须属于同一个项目"
            );
        }
        task.setProjectId(projectId);
        task.setAnnotatorId(userId);
        task.setStatus("PENDING");
        task.setCreatorId(userId);
        annotationTaskMapper.insert(task);

        for (Dataset dataset : datasets) {
            Dataset update = new Dataset();
            update.setId(dataset.getId());
            update.setTaskId(task.getId());
            update.setAnnotationStatus("ASSIGNED");
            datasetMapper.updateById(update);
        }
        annotationTaskWorkflowMapper.assignDatasets(
                task.getId(),
                datasets.stream().map(Dataset::getId).toList()
        );
        return new TaskBrief(
                task.getId(),
                task.getName(),
                task.getStatus(),
                datasets.size(),
                displayName(userId),
                task.getCreatedAt()
        );
    }

    @Override
    @Transactional
    public void addTags(TagBatchRequest request, long userId, boolean admin) {
        List<Dataset> datasets = requireActive(request.ids(), userId, admin);
        guardOpenShared(datasets);
        List<String> names = normalizeNames(request.tags());
        for (Dataset dataset : datasets) {
            linkTags(dataset.getId(), names);
        }
    }

    @Override
    @Transactional
    public void removeTags(TagBatchRequest request, long userId, boolean admin) {
        List<Dataset> datasets = requireActive(request.ids(), userId, admin);
        guardOpenShared(datasets);
        for (String name : normalizeNames(request.tags())) {
            DatasetTag tag = datasetTagMapper.findByName(name);
            if (tag == null) {
                continue;
            }
            for (Dataset dataset : datasets) {
                datasetTagMapper.deleteRel(dataset.getId(), tag.getId());
            }
        }
        datasetTagMapper.deleteUnusedTags();
    }

    @Override
    @Transactional
    public List<ItemResult> refreshMetadata(List<Long> rawIds, long userId, boolean admin) {
        List<Dataset> datasets = requireActive(rawIds, userId, admin);
        guardOpenShared(datasets);
        List<ItemResult> results = new ArrayList<>();
        for (Dataset dataset : datasets) {
            setMetadata(dataset.getId(), "PROCESSING");
            StoredFile file = storedFileMapper.selectById(dataset.getFileId());
            if (file == null || !READY.equals(file.getStatus())) {
                setMetadata(dataset.getId(), "FAILED");
                results.add(new ItemResult(dataset.getId(), "FAILED", "文件损坏或格式错误"));
                continue;
            }
            Dataset update = new Dataset();
            update.setId(dataset.getId());
            update.setSizeBytes(file.getSizeBytes());
            update.setMetadataStatus(READY);
            datasetMapper.updateById(update);
            results.add(new ItemResult(dataset.getId(), READY, null));
        }
        return results;
    }

    @Override
    @Transactional
    public void softDelete(List<Long> rawIds, long userId, boolean admin) {
        List<Dataset> datasets = requireActive(rawIds, userId, admin);
        guardOpenShared(datasets);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (Dataset dataset : datasets) {
            Dataset update = new Dataset();
            update.setId(dataset.getId());
            update.setDeleted(true);
            update.setDeletedAt(now);
            datasetMapper.updateById(update);
        }
    }

    @Override
    @Transactional
    public void importToProject(ImportRequest request, long userId, boolean admin) {
        List<Dataset> datasets = requireActive(request.ids(), userId, admin);
        guardOpenShared(datasets);
        if (datasetMapper.countProjectById(request.projectId()) == 0) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "目标项目不存在");
        }
        if (!admin && datasetMapper.countMembership(request.projectId(), userId) == 0) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "需要项目成员身份");
        }
        for (Dataset dataset : datasets) {
            Dataset update = new Dataset();
            update.setId(dataset.getId());
            update.setProjectId(request.projectId());
            datasetMapper.updateById(update);
        }
    }

    @Override
    @Transactional
    public void assignRobot(RobotRequest request, long userId, boolean admin) {
        List<Dataset> datasets = requireActive(request.ids(), userId, admin);
        guardOpenShared(datasets);
        String robot = blankToNull(request.robotCode());
        if (robot != null) {
            var catalogRobot = robotMapper.selectAnyByName(robot);
            if (catalogRobot == null || Boolean.TRUE.equals(catalogRobot.getDeleted())) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Robot is not active");
            }
            robot = catalogRobot.getName();
        }
        for (Dataset dataset : datasets) {
            datasetMapper.update(
                    null,
                    new LambdaUpdateWrapper<Dataset>()
                            .eq(Dataset::getId, dataset.getId())
                            .set(Dataset::getRobotCode, robot)
            );
        }
    }

    @Override
    public DatasetStats stats(List<Long> rawIds, long userId, boolean admin) {
        List<Dataset> datasets = requireActive(rawIds, userId, admin);
        List<Long> ids = datasets.stream().map(Dataset::getId).toList();

        DatasetSelectionAggRow selection = datasetMapper.selectSelectionAgg(ids);
        AnnotationAggRow agg = datasetAnnotationMapper.aggregateForDatasets(ids);

        long total = selection.total;
        BigDecimal denominator = BigDecimal.valueOf(Math.max(total, 1));
        BigDecimal averageAnnotations = BigDecimal.valueOf(agg.totalAnnotations)
                .divide(denominator, 2, RoundingMode.HALF_UP);
        BigDecimal averageDuration = agg.coveredDuration
                .divide(denominator, 2, RoundingMode.HALF_UP);
        BigDecimal checkedRate = agg.reviewedCount == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(agg.reviewedQualified)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(agg.reviewedCount), 2, RoundingMode.HALF_UP);

        return new DatasetStats(
                total,
                selection.totalDuration,
                agg.totalAnnotations,
                agg.qualifiedAnnotations,
                averageAnnotations,
                agg.coveredDuration,
                averageDuration,
                agg.invalidDatasetCount,
                checkedRate,
                agg.invalidCollect,
                agg.semanticUncorrected,
                agg.semanticCorrected
        );
    }

    @Override
    public List<DatasetView> trash(long userId, boolean admin) {
        DatasetFilter filter = new DatasetFilter();
        filter.setCurrentUserId(userId);
        filter.setAdmin(admin);
        List<DatasetListRow> rows = datasetMapper.selectDeleted(filter);
        Map<Long, List<String>> tagMap = tagMap(rows.stream().map(row -> row.id).toList());
        return rows.stream()
                .map(row -> toView(row, tagMap.getOrDefault(row.id, List.of())))
                .toList();
    }

    @Override
    @Transactional
    public void restore(long id, long userId, boolean admin) {
        Dataset dataset = datasetMapper.selectById(id);
        if (dataset == null || !Boolean.TRUE.equals(dataset.getDeleted())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "回收站中无此数据");
        }
        if (!admin && userId != dataset.getUploaderId()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        Dataset active = datasetMapper.selectActiveByName(dataset.getName());
        if (active != null) {
            throw new BusinessException(ErrorCode.CONFLICT, "已存在同名数据集，无法恢复");
        }
        Dataset update = new Dataset();
        update.setId(id);
        update.setDeleted(false);
        datasetMapper.updateById(update);
        datasetMapper.clearDeletedAt(id);
    }

    @Override
    public List<String> robotOptions() {
        return robotMapper.selectActiveNames();
    }

    @Override
    public List<String> tagOptions() {
        return datasetTagMapper.selectAllTagNames();
    }

    @Override
    public List<OptionRow> userOptions() {
        return datasetMapper.selectUserOptions();
    }

    private void prepare(DatasetFilter filter) {
        if (!"ASC".equalsIgnoreCase(filter.getSortDir())
                && !"DESC".equalsIgnoreCase(filter.getSortDir())) {
            filter.setSortDir("DESC");
        }
        String scope = filter.getScope() == null
                ? "ALL"
                : filter.getScope().trim().toUpperCase(Locale.ROOT);
        filter.setScope(scope);
        filter.setOffset((long) (filter.getPage() - 1) * filter.getSize());
    }

    private List<Dataset> requireActive(List<Long> rawIds, long userId, boolean admin) {
        List<Long> ids = rawIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "未选择数据");
        }
        Map<Long, Dataset> active = new HashMap<>();
        for (Dataset dataset : datasetMapper.selectBatchIds(ids)) {
            if (!Boolean.TRUE.equals(dataset.getDeleted())) {
                active.put(dataset.getId(), dataset);
            }
        }
        List<Dataset> result = new ArrayList<>();
        for (Long id : ids) {
            Dataset dataset = active.get(id);
            if (dataset == null) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "数据集不存在：" + id);
            }
            if (!admin && userId != dataset.getUploaderId()) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作他人数据集：" + id);
            }
            result.add(dataset);
        }
        return result;
    }

    private void guardOpenShared(List<Dataset> datasets) {
        boolean blocked = datasets.stream()
                .anyMatch(dataset -> Boolean.TRUE.equals(dataset.getOpenShared()));
        if (blocked) {
            throw new BusinessException(ErrorCode.CONFLICT, OPEN_SHARED_MESSAGE);
        }
    }

    private long createDataset(
            StoredFile file,
            DatasetRegisterRequest request,
            long userId,
            String name,
            String dataType
    ) {
        Dataset dataset = new Dataset();
        dataset.setName(name);
        dataset.setFileId(file.getId());
        dataset.setDataType(dataType);
        dataset.setSizeBytes(file.getSizeBytes());
        dataset.setDurationSeconds(request.durationSeconds());
        dataset.setAnnotationStatus("UNASSIGNED");
        dataset.setProjectId(request.projectId());
        dataset.setRobotCode(blankToNull(request.robotCode()));
        dataset.setCollectorId(request.collectorId());
        dataset.setSourceTaskCode(blankToNull(request.sourceTaskCode()));
        dataset.setUploaderId(userId);
        dataset.setMetadataStatus(READY);
        dataset.setOpenShared(Boolean.TRUE.equals(request.openShared()));
        dataset.setDeleted(false);
        datasetMapper.insert(dataset);
        return dataset.getId();
    }

    private void restoreExistingDataset(
            Dataset dataset,
            StoredFile file,
            DatasetRegisterRequest request,
            String dataType
    ) {
        Dataset update = new Dataset();
        update.setId(dataset.getId());
        update.setFileId(file.getId());
        update.setSizeBytes(file.getSizeBytes());
        update.setDataType(dataType);
        update.setDeleted(false);
        if (request.durationSeconds() != null) {
            update.setDurationSeconds(request.durationSeconds());
        }
        if (request.projectId() != null) {
            update.setProjectId(request.projectId());
        }
        if (request.robotCode() != null) {
            update.setRobotCode(blankToNull(request.robotCode()));
        }
        if (request.collectorId() != null) {
            update.setCollectorId(request.collectorId());
        }
        if (request.sourceTaskCode() != null) {
            update.setSourceTaskCode(blankToNull(request.sourceTaskCode()));
        }
        datasetMapper.updateById(update);
        datasetMapper.clearDeletedAt(dataset.getId());
    }

    private void linkTags(long datasetId, List<String> rawNames) {
        for (String name : normalizeNames(rawNames)) {
            DatasetTag tag = datasetTagMapper.findByName(name);
            if (tag == null) {
                tag = new DatasetTag();
                tag.setName(name);
                datasetTagMapper.insert(tag);
            }
            datasetTagMapper.insertRel(datasetId, tag.getId());
        }
    }

    private List<String> normalizeNames(List<String> rawNames) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (String raw : rawNames) {
            if (raw != null && !raw.trim().isEmpty()) {
                names.add(raw.trim());
            }
        }
        return List.copyOf(names);
    }

    private void setMetadata(long id, String status) {
        Dataset update = new Dataset();
        update.setId(id);
        update.setMetadataStatus(status);
        datasetMapper.updateById(update);
    }

    private String displayName(long userId) {
        UserAccount user = userAccountMapper.selectById(userId);
        return user == null ? null : user.getDisplayName();
    }

    private Preview buildPreview(DatasetListRow row) {
        StoredFile file = row.fileId == null ? null : storedFileMapper.selectById(row.fileId);
        if (file == null || !READY.equals(file.getStatus())) {
            return null;
        }
        try {
            String disposition = ContentDisposition.inline()
                    .filename(file.getOriginalName(), StandardCharsets.UTF_8)
                    .build()
                    .toString();
            OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC)
                    .plus(minioProperties.previewTtl());
            String url = objectStorage.presignGet(
                    file.getObjectKey(),
                    disposition,
                    minioProperties.previewTtl()
            );
            return new Preview(url, expiresAt);
        } catch (ObjectStorageException exception) {
            return null;
        }
    }

    private String resolveDataType(String requested, StoredFile file) {
        if (requested != null && !requested.isBlank()) {
            return requested.trim().toUpperCase(Locale.ROOT);
        }
        String contentType = file.getContentType() == null
                ? ""
                : file.getContentType().toLowerCase(Locale.ROOT);
        if (contentType.startsWith("video/")) {
            return "VIDEO";
        }
        if (contentType.startsWith("audio/")) {
            return "AUDIO";
        }
        return switch (extension(file.getOriginalName())) {
            case "mp4", "avi", "mov" -> "VIDEO";
            case "mp3", "wav" -> "AUDIO";
            case "mcap" -> "MCAP";
            default -> throw new BusinessException(
                    ErrorCode.INVALID_ARGUMENT,
                    "无法识别数据类型，请显式指定"
            );
        };
    }

    private String extension(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    private String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private Map<Long, List<String>> tagMap(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return datasetTagMapper.selectTagsForDatasets(ids).stream()
                .collect(Collectors.groupingBy(
                        row -> row.datasetId,
                        Collectors.mapping(row -> row.tagName, Collectors.toList())
                ));
    }

    private DatasetView toView(DatasetListRow row, List<String> tags) {
        return new DatasetView(
                row.id,
                row.name,
                row.dataType,
                row.sizeBytes,
                row.durationSeconds,
                row.annotationStatus,
                row.projectId,
                row.projectName,
                row.robotCode,
                row.collectorId,
                row.collectorName,
                row.sourceTaskCode,
                row.uploaderId,
                row.uploaderName,
                row.metadataStatus,
                Boolean.TRUE.equals(row.openShared),
                List.copyOf(tags),
                buildPreview(row),
                row.createdAt,
                row.updatedAt
        );
    }
}

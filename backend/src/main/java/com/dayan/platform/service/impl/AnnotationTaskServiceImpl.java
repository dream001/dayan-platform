package com.dayan.platform.service.impl;

import com.dayan.platform.common.api.ErrorCode;
import com.dayan.platform.common.exception.BusinessException;
import com.dayan.platform.dto.AnnotationTaskDtos.BatchAnnotationRequest;
import com.dayan.platform.dto.AnnotationTaskDtos.CreateRequest;
import com.dayan.platform.dto.AnnotationTaskDtos.DatasetReviewRequest;
import com.dayan.platform.dto.AnnotationTaskDtos.StatusRequest;
import com.dayan.platform.dto.AnnotationTaskDtos.UpdateRequest;
import com.dayan.platform.model.AnnotationTask;
import com.dayan.platform.repository.mapper.AnnotationTaskWorkflowMapper;
import com.dayan.platform.repository.mapper.ProjectMemberMapper;
import com.dayan.platform.repository.query.AnnotationDatasetRow;
import com.dayan.platform.repository.query.AnnotationTaskSummaryRow;
import com.dayan.platform.service.AnnotationTaskService;
import com.dayan.platform.vo.AnnotationTaskViews.DatasetItem;
import com.dayan.platform.vo.AnnotationTaskViews.PersonOption;
import com.dayan.platform.vo.AnnotationTaskViews.StatusCounts;
import com.dayan.platform.vo.AnnotationTaskViews.TaskDetail;
import com.dayan.platform.vo.AnnotationTaskViews.TaskOptions;
import com.dayan.platform.vo.AnnotationTaskViews.TaskSummary;
import com.dayan.platform.vo.PageResponse;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AnnotationTaskServiceImpl implements AnnotationTaskService {

    private static final List<String> STATUSES = List.of(
            "PENDING",
            "WORKING",
            "REVIEW_PENDING",
            "REJECTED",
            "APPROVED",
            "SUBMITTED"
    );
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "PENDING", Set.of("WORKING"),
            "WORKING", Set.of("REVIEW_PENDING"),
            "REVIEW_PENDING", Set.of("APPROVED", "REJECTED", "WORKING"),
            "REJECTED", Set.of("REVIEW_PENDING", "APPROVED"),
            "APPROVED", Set.of("REJECTED", "SUBMITTED"),
            "SUBMITTED", Set.of()
    );

    private final AnnotationTaskWorkflowMapper taskMapper;
    private final ProjectMemberMapper projectMemberMapper;

    public AnnotationTaskServiceImpl(
            AnnotationTaskWorkflowMapper taskMapper,
            ProjectMemberMapper projectMemberMapper
    ) {
        this.taskMapper = taskMapper;
        this.projectMemberMapper = projectMemberMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TaskSummary> page(
            int page,
            int size,
            String keyword,
            String status,
            Long projectId,
            Long annotatorId,
            Long reviewerId,
            LocalDate createdDate,
            long userId,
            boolean platformManager
    ) {
        String normalizedStatus = normalizeStatus(status);
        String date = createdDate == null ? null : createdDate.toString();
        long total = taskMapper.countSummaries(
                userId,
                platformManager,
                normalizeNullable(keyword),
                normalizedStatus,
                projectId,
                annotatorId,
                reviewerId,
                date
        );
        List<TaskSummary> items = taskMapper.selectSummaryPage(
                userId,
                platformManager,
                normalizeNullable(keyword),
                normalizedStatus,
                projectId,
                annotatorId,
                reviewerId,
                date,
                (long) (page - 1) * size,
                size
        ).stream().map(this::summary).toList();
        return PageResponse.of(page, size, total, items);
    }

    @Override
    @Transactional(readOnly = true)
    public StatusCounts counts(long userId, boolean platformManager) {
        Map<String, Long> statuses = new LinkedHashMap<>();
        STATUSES.forEach(status -> statuses.put(status, 0L));
        taskMapper.selectStatusCounts(userId, platformManager)
                .forEach(row -> statuses.put(row.getStatus(), row.getCount()));
        long total = statuses.values().stream().mapToLong(Long::longValue).sum();
        return new StatusCounts(total, statuses);
    }

    @Override
    @Transactional(readOnly = true)
    public TaskOptions options(Long projectId, long userId, boolean platformManager) {
        var projects = taskMapper.selectProjectOptions(userId, platformManager);
        if (projectId == null) {
            return new TaskOptions(
                    projects,
                    List.of(),
                    List.of(),
                    taskMapper.selectAvailableDatasets(null)
            );
        }
        boolean accessible = projects.stream().anyMatch(project -> project.id() == projectId);
        if (!accessible) {
            throw forbidden();
        }
        return new TaskOptions(
                projects,
                taskMapper.selectPeople(projectId, "ANNOTATOR"),
                taskMapper.selectPeople(projectId, "REVIEWER"),
                taskMapper.selectAvailableDatasets(projectId)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public TaskDetail detail(
            long id,
            long userId,
            boolean platformManager,
            boolean canExecute,
            boolean canReview
    ) {
        AnnotationTaskSummaryRow task = requireTask(id);
        requireAccessible(task, userId, platformManager);
        return detail(task, userId, platformManager, canExecute, canReview);
    }

    @Override
    @Transactional
    public List<TaskDetail> create(
            CreateRequest request,
            long userId,
            boolean platformManager,
            boolean canExecute,
            boolean canReview
    ) {
        requireProjectManagement(request.projectId(), userId, platformManager);
        List<Long> annotatorIds = new ArrayList<>(new LinkedHashSet<>(request.annotatorIds()));
        List<Long> reviewerIds = request.reviewerIds() == null
                ? List.of()
                : new ArrayList<>(new LinkedHashSet<>(request.reviewerIds()));
        validatePeople(request.projectId(), annotatorIds, reviewerIds);

        List<Long> datasetIds = new ArrayList<>(new LinkedHashSet<>(request.datasetIds()));
        if (datasetIds.size() < annotatorIds.size()) {
            throw invalid("Dataset count must not be smaller than annotator count");
        }
        List<Long> availableIds = taskMapper.lockAvailableDatasetIds(request.projectId(), datasetIds);
        if (availableIds.size() != datasetIds.size()) {
            throw conflict("One or more datasets are unavailable or assigned to another task");
        }
        if (Boolean.TRUE.equals(request.randomOrder())) {
            Collections.shuffle(datasetIds);
        }

        String baseName = StringUtils.hasText(request.name())
                ? request.name().trim()
                : LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE);
        int baseShare = datasetIds.size() / annotatorIds.size();
        int remainder = datasetIds.size() % annotatorIds.size();
        int cursor = 0;
        List<TaskDetail> created = new ArrayList<>();
        Map<Long, PersonOption> people = taskMapper.selectPeople(request.projectId(), "ANNOTATOR")
                .stream()
                .collect(Collectors.toMap(PersonOption::id, Function.identity()));

        for (int index = 0; index < annotatorIds.size(); index++) {
            long annotatorId = annotatorIds.get(index);
            Long reviewerId = reviewerIds.isEmpty()
                    ? null
                    : reviewerIds.get(index % reviewerIds.size());
            int share = baseShare + (index < remainder ? 1 : 0);
            List<Long> assignedIds = datasetIds.subList(cursor, cursor + share);
            cursor += share;

            AnnotationTask task = new AnnotationTask();
            String requestedName = baseName + "_" + people.get(annotatorId).displayName();
            task.setName(uniqueName(requestedName));
            task.setProjectId(request.projectId());
            task.setAnnotatorId(annotatorId);
            task.setReviewerId(reviewerId);
            task.setCreatorId(userId);
            task.setStatus("PENDING");
            try {
                taskMapper.insert(task);
                taskMapper.assignDatasets(task.getId(), assignedIds);
                taskMapper.markDatasetsAssigned(task.getId(), assignedIds);
                taskMapper.syncDatasetCount(task.getId());
            } catch (DataIntegrityViolationException exception) {
                throw conflict("Task name or dataset assignment conflicts with existing data");
            }
            created.add(detail(
                    requireTask(task.getId()),
                    userId,
                    platformManager,
                    canExecute,
                    canReview
            ));
        }
        return created;
    }

    @Override
    @Transactional
    public TaskDetail update(
            long id,
            UpdateRequest request,
            long userId,
            boolean platformManager,
            boolean canExecute,
            boolean canReview
    ) {
        if (!platformManager) {
            throw forbidden();
        }
        AnnotationTaskSummaryRow task = requireTask(id);
        requireAccessible(task, userId, true);
        if (!Objects.equals(task.getProjectId(), request.projectId()) && task.getDatasetCount() > 0) {
            throw conflict("A task with datasets cannot be moved to another project");
        }
        requireProjectManagement(request.projectId(), userId, true);
        validatePeople(
                request.projectId(),
                List.of(request.annotatorId()),
                request.reviewerId() == null ? List.of() : List.of(request.reviewerId())
        );
        String name = request.name().trim();
        if (!task.getName().equalsIgnoreCase(name) && taskMapper.countByName(name) > 0) {
            throw invalid("Task name already exists");
        }
        task.setName(name);
        task.setProjectId(request.projectId());
        task.setAnnotatorId(request.annotatorId());
        task.setReviewerId(request.reviewerId());
        task.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        taskMapper.updateById(task);
        return detail(id, userId, platformManager, canExecute, canReview);
    }

    @Override
    @Transactional
    public TaskDetail changeStatus(
            long id,
            StatusRequest request,
            long userId,
            boolean platformManager,
            boolean canExecute,
            boolean canReview
    ) {
        AnnotationTaskSummaryRow task = requireTask(id);
        requireAccessible(task, userId, platformManager);
        String target = normalizeStatus(request.status());
        if (!TRANSITIONS.getOrDefault(task.getStatus(), Set.of()).contains(target)) {
            throw conflict("Task status transition is not allowed");
        }
        if (!canSetStatus(task, target, userId, platformManager, canExecute, canReview)) {
            throw forbidden();
        }
        String reason = normalizeNullable(request.rejectionReason());
        if ("REJECTED".equals(target) && reason == null) {
            throw invalid("Rejection reason is required");
        }
        task.setStatus(target);
        task.setRejectionReason("REJECTED".equals(target) ? reason : null);
        task.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        taskMapper.updateById(task);
        return detail(id, userId, platformManager, canExecute, canReview);
    }

    @Override
    @Transactional
    public TaskDetail reviewDataset(
            long id,
            long relationId,
            DatasetReviewRequest request,
            long userId,
            boolean platformManager,
            boolean canExecute,
            boolean canReview
    ) {
        AnnotationTaskSummaryRow task = requireTask(id);
        requireAccessible(task, userId, platformManager);
        boolean reviewer = canReview
                && (platformManager || task.getReviewerId() != null && task.getReviewerId() == userId
                || task.getCreatorId() == userId);
        boolean annotator = canExecute && task.getAnnotatorId() == userId;
        if (!reviewer && !annotator) {
            throw forbidden();
        }
        String result = normalizeNullable(request.result());
        String reason = normalizeNullable(request.rejectionReason());
        if (annotator && !reviewer && "VALID".equals(result)) {
            result = null;
            reason = null;
        }
        if ("INVALID".equals(result) && reason == null) {
            throw invalid("Dataset rejection reason is required");
        }
        if (taskMapper.reviewDataset(id, relationId, result, reason) != 1) {
            throw notFound("Task dataset not found");
        }
        return detail(id, userId, platformManager, canExecute, canReview);
    }

    @Override
    @Transactional
    public TaskDetail batchAnnotate(
            long id,
            BatchAnnotationRequest request,
            long userId,
            boolean platformManager,
            boolean canExecute,
            boolean canReview
    ) {
        AnnotationTaskSummaryRow task = requireTask(id);
        requireAccessible(task, userId, platformManager);
        if (!canExecute
                || !platformManager && task.getAnnotatorId() != userId && task.getCreatorId() != userId) {
            throw forbidden();
        }
        switch (request.mode()) {
            case "QUICK" -> {
                String description = normalizeNullable(request.description());
                if (description == null) {
                    throw invalid("Annotation description is required");
                }
                taskMapper.quickAnnotate(id, description);
            }
            case "COPY" -> {
                if (request.sourceRelationId() == null) {
                    throw invalid("Source dataset is required");
                }
                if (taskMapper.copyAnnotation(id, request.sourceRelationId()) == 0) {
                    throw notFound("Source task dataset not found");
                }
            }
            case "REPLACE" -> {
                String findText = normalizeNullable(request.findText());
                if (findText == null) {
                    throw invalid("Replacement source text is required");
                }
                taskMapper.replaceAnnotation(
                        id,
                        findText,
                        request.replaceText() == null ? "" : request.replaceText()
                );
            }
            default -> throw invalid("Invalid batch annotation mode");
        }
        return detail(id, userId, platformManager, canExecute, canReview);
    }

    @Override
    @Transactional
    public void delete(long id, long userId, boolean platformManager) {
        if (!platformManager) {
            throw forbidden();
        }
        AnnotationTaskSummaryRow task = requireTask(id);
        requireAccessible(task, userId, true);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        task.setDeletedAt(now);
        task.setUpdatedAt(now);
        taskMapper.updateById(task);
        taskMapper.unassignDatasets(id);
        taskMapper.markDatasetsUnassigned(id);
        taskMapper.syncDatasetCount(id);
    }

    private TaskDetail detail(
            AnnotationTaskSummaryRow task,
            long userId,
            boolean platformManager,
            boolean canExecute,
            boolean canReview
    ) {
        return new TaskDetail(
                summary(task),
                taskMapper.selectDatasets(task.getId()).stream().map(this::dataset).toList(),
                allowedTransitions(task, userId, platformManager, canExecute, canReview),
                platformManager,
                platformManager,
                canExecute && (platformManager || task.getAnnotatorId() == userId
                        || task.getCreatorId() == userId),
                canReview && (platformManager || task.getReviewerId() != null
                        && task.getReviewerId() == userId || task.getCreatorId() == userId)
        );
    }

    private List<String> allowedTransitions(
            AnnotationTaskSummaryRow task,
            long userId,
            boolean platformManager,
            boolean canExecute,
            boolean canReview
    ) {
        return TRANSITIONS.getOrDefault(task.getStatus(), Set.of()).stream()
                .filter(target -> canSetStatus(
                        task,
                        target,
                        userId,
                        platformManager,
                        canExecute,
                        canReview
                ))
                .sorted((left, right) -> Integer.compare(STATUSES.indexOf(left), STATUSES.indexOf(right)))
                .toList();
    }

    private boolean canSetStatus(
            AnnotationTaskSummaryRow task,
            String target,
            long userId,
            boolean platformManager,
            boolean canExecute,
            boolean canReview
    ) {
        if (platformManager) {
            return true;
        }
        if (Set.of("WORKING", "REVIEW_PENDING").contains(target)) {
            return canExecute
                    && (task.getAnnotatorId() == userId || task.getCreatorId() == userId)
                    && !"APPROVED".equals(task.getStatus());
        }
        if (Set.of("APPROVED", "REJECTED").contains(target)) {
            return canReview
                    && (task.getReviewerId() != null && task.getReviewerId() == userId
                    || task.getCreatorId() == userId);
        }
        return false;
    }

    private void validatePeople(long projectId, List<Long> annotatorIds, List<Long> reviewerIds) {
        Set<Long> eligibleAnnotators = taskMapper.selectPeople(projectId, "ANNOTATOR").stream()
                .map(PersonOption::id)
                .collect(Collectors.toSet());
        Set<Long> eligibleReviewers = taskMapper.selectPeople(projectId, "REVIEWER").stream()
                .map(PersonOption::id)
                .collect(Collectors.toSet());
        if (!eligibleAnnotators.containsAll(annotatorIds)) {
            throw invalid("Annotator must be an active project annotator");
        }
        if (!eligibleReviewers.containsAll(reviewerIds)) {
            throw invalid("Reviewer must be an active project reviewer");
        }
    }

    private void requireProjectManagement(long projectId, long userId, boolean platformManager) {
        if (platformManager) {
            return;
        }
        String role = projectMemberMapper.selectActiveRole(projectId, userId);
        if (!Set.of("PROJECT_ADMIN", "PROJECT_MANAGER").contains(role)) {
            throw forbidden();
        }
    }

    private void requireAccessible(
            AnnotationTaskSummaryRow task,
            long userId,
            boolean platformManager
    ) {
        if (platformManager
                || task.getCreatorId() == userId
                || task.getAnnotatorId() == userId
                || task.getReviewerId() != null && task.getReviewerId() == userId
                || projectMemberMapper.selectActiveRole(task.getProjectId(), userId) != null) {
            return;
        }
        throw forbidden();
    }

    private AnnotationTaskSummaryRow requireTask(long id) {
        AnnotationTaskSummaryRow task = taskMapper.selectSummaryById(id);
        if (task == null) {
            throw notFound("Annotation task not found");
        }
        return task;
    }

    private TaskSummary summary(AnnotationTaskSummaryRow row) {
        long datasetCount = row.getDatasetCount() == null ? 0 : row.getDatasetCount();
        long reviewedCount = row.getReviewedCount() == null ? 0 : row.getReviewedCount();
        int progress = datasetCount == 0 ? 0 : (int) Math.round(reviewedCount * 100.0 / datasetCount);
        return new TaskSummary(
                row.getId(),
                row.getName(),
                row.getProjectId(),
                row.getProjectName(),
                row.getAnnotatorId(),
                row.getAnnotatorName(),
                row.getReviewerId(),
                row.getReviewerName(),
                row.getCreatorId(),
                row.getCreatorName(),
                row.getStatus(),
                row.getRejectionReason(),
                datasetCount,
                reviewedCount,
                progress,
                row.getCreatedAt(),
                row.getUpdatedAt()
        );
    }

    private DatasetItem dataset(AnnotationDatasetRow row) {
        return new DatasetItem(
                row.getId(),
                row.getDatasetId(),
                row.getName(),
                row.getDataType(),
                row.getSizeBytes(),
                row.getAnnotationDescription(),
                row.getCheckResult(),
                row.getRejectionReason(),
                row.getCreatedAt()
        );
    }

    private String uniqueName(String requestedName) {
        String candidate = requestedName.length() <= 120
                ? requestedName
                : requestedName.substring(0, 120);
        if (taskMapper.countByName(candidate) == 0) {
            return candidate;
        }
        int suffix = 2;
        while (suffix < 10_000) {
            String marker = " (" + suffix + ")";
            int maxBaseLength = 120 - marker.length();
            candidate = requestedName.substring(0, Math.min(requestedName.length(), maxBaseLength)) + marker;
            if (taskMapper.countByName(candidate) == 0) {
                return candidate;
            }
            suffix++;
        }
        throw conflict("Unable to generate a unique task name");
    }

    private String normalizeStatus(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String status = value.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(status)) {
            throw invalid("Invalid task status");
        }
        return status;
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_ARGUMENT, message);
    }

    private BusinessException conflict(String message) {
        return new BusinessException(ErrorCode.CONFLICT, message);
    }

    private BusinessException forbidden() {
        return new BusinessException(ErrorCode.FORBIDDEN, "Annotation task access denied");
    }

    private BusinessException notFound(String message) {
        return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, message);
    }
}

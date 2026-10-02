package com.dayan.platform.service.impl;

import com.dayan.platform.common.api.ErrorCode;
import com.dayan.platform.common.exception.BusinessException;
import com.dayan.platform.dto.CollectionTaskDtos.CollectionStatusRequest;
import com.dayan.platform.dto.CollectionTaskDtos.CollectionStepRequest;
import com.dayan.platform.dto.CollectionTaskDtos.CollectionTaskRequest;
import com.dayan.platform.model.CollectionTask;
import com.dayan.platform.model.Dataset;
import com.dayan.platform.repository.mapper.CollectionTaskMapper;
import com.dayan.platform.repository.mapper.DatasetMapper;
import com.dayan.platform.repository.mapper.ProjectMemberMapper;
import com.dayan.platform.repository.query.CollectionTaskDetailRows.AssigneeRow;
import com.dayan.platform.repository.query.CollectionTaskDetailRows.DatasetRow;
import com.dayan.platform.repository.query.CollectionTaskDetailRows.OptionRow;
import com.dayan.platform.repository.query.CollectionTaskDetailRows.StatusCountRow;
import com.dayan.platform.repository.query.CollectionTaskDetailRows.StepRow;
import com.dayan.platform.repository.query.CollectionTaskSummaryRow;
import com.dayan.platform.service.CollectionTaskService;
import com.dayan.platform.vo.CollectionTaskViews.CollectionAssignee;
import com.dayan.platform.vo.CollectionTaskViews.CollectionDataset;
import com.dayan.platform.vo.CollectionTaskViews.CollectionOptions;
import com.dayan.platform.vo.CollectionTaskViews.CollectionProjectOption;
import com.dayan.platform.vo.CollectionTaskViews.CollectionStatusCounts;
import com.dayan.platform.vo.CollectionTaskViews.CollectionStep;
import com.dayan.platform.vo.CollectionTaskViews.CollectionTaskDetail;
import com.dayan.platform.vo.CollectionTaskViews.CollectionTaskSummary;
import com.dayan.platform.vo.PageResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CollectionTaskServiceImpl implements CollectionTaskService {

    private static final List<String> STATUSES = List.of(
            "PENDING",
            "WORKING",
            "REVIEW_PENDING",
            "REJECTED",
            "APPROVED",
            "SUBMITTED"
    );

    private final CollectionTaskMapper collectionTaskMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final DatasetMapper datasetMapper;
    private final ObjectMapper objectMapper;

    public CollectionTaskServiceImpl(
            CollectionTaskMapper collectionTaskMapper,
            ProjectMemberMapper projectMemberMapper,
            DatasetMapper datasetMapper,
            ObjectMapper objectMapper
    ) {
        this.collectionTaskMapper = collectionTaskMapper;
        this.projectMemberMapper = projectMemberMapper;
        this.datasetMapper = datasetMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CollectionTaskSummary> page(
            int page,
            int size,
            String keyword,
            Long collectorId,
            String status,
            Access access
    ) {
        String normalizedKeyword = normalize(keyword);
        String normalizedStatus = normalizeStatus(status);
        long total = collectionTaskMapper.countAccessible(
                access.userId(),
                access.administrator(),
                normalizedKeyword,
                collectorId,
                normalizedStatus
        );
        List<CollectionTaskSummary> items = collectionTaskMapper.selectSummaryPage(
                access.userId(),
                access.administrator(),
                normalizedKeyword,
                collectorId,
                normalizedStatus,
                (long) (page - 1) * size,
                size
        ).stream().map(this::summary).toList();
        return PageResponse.of(page, size, total, items);
    }

    @Override
    @Transactional(readOnly = true)
    public CollectionStatusCounts statusCounts(Access access) {
        Map<String, Long> statuses = new LinkedHashMap<>();
        STATUSES.forEach(status -> statuses.put(status, 0L));
        long total = 0;
        for (StatusCountRow row : collectionTaskMapper.selectStatusCounts(
                access.userId(),
                access.administrator()
        )) {
            statuses.put(row.getStatus(), row.getCount());
            total += row.getCount();
        }
        return new CollectionStatusCounts(total, statuses);
    }

    @Override
    @Transactional(readOnly = true)
    public CollectionTaskDetail detail(long id, Access access) {
        CollectionTaskSummaryRow row = requireAccessible(id, access);
        return detail(row, access);
    }

    @Override
    @Transactional
    public CollectionTaskDetail create(CollectionTaskRequest request, Access access) {
        requireManage(access);
        validateRequest(request, access);
        requireUniqueName(request.name(), null);
        CollectionTask task = new CollectionTask();
        apply(task, request);
        task.setStatus("PENDING");
        task.setCreatedBy(access.userId());
        try {
            collectionTaskMapper.insert(task);
            replaceChildren(task.getId(), request);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "Collection task name already exists");
        }
        return detail(task.getId(), access);
    }

    @Override
    @Transactional
    public CollectionTaskDetail update(long id, CollectionTaskRequest request, Access access) {
        requireManage(access);
        CollectionTaskSummaryRow existing = requireAccessible(id, access);
        requireEditable(existing, access);
        if ("SUBMITTED".equals(existing.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Submitted collection tasks cannot be edited");
        }
        validateRequest(request, access);
        requireUniqueName(request.name(), id);
        apply(existing, request);
        existing.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        try {
            collectionTaskMapper.updateById(existing);
            replaceChildren(id, request);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "Collection task name already exists");
        }
        return detail(id, access);
    }

    @Override
    @Transactional
    public CollectionTaskDetail changeStatus(
            long id,
            CollectionStatusRequest request,
            Access access
    ) {
        CollectionTaskSummaryRow task = requireAccessible(id, access);
        List<String> allowed = allowedTransitions(task.getStatus(), access);
        if (!allowed.contains(request.status())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Collection task status transition is not allowed");
        }
        task.setStatus(request.status());
        task.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        collectionTaskMapper.updateById(task);
        return detail(id, access);
    }

    @Override
    @Transactional
    public void delete(long id, Access access) {
        requireManage(access);
        CollectionTaskSummaryRow task = requireAccessible(id, access);
        requireEditable(task, access);
        if (collectionTaskMapper.softDelete(id) == 0) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Collection task not found");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public CollectionOptions options(Long projectId, Access access) {
        List<CollectionProjectOption> projects = collectionTaskMapper.selectProjectOptions(
                access.userId(),
                access.administrator()
        ).stream().map(row -> new CollectionProjectOption(row.getId(), row.getName())).toList();
        if (projectId == null) {
            List<CollectionAssignee> collectors = collectionTaskMapper.selectAccessibleCollectorOptions(
                    access.userId(),
                    access.administrator()
            ).stream().map(this::assignee).toList();
            return new CollectionOptions(projects, collectors);
        }
        boolean projectAllowed = projects.stream().anyMatch(project -> project.id() == projectId);
        if (!projectAllowed) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN,
                    "You do not have permission to manage collection tasks in this project"
            );
        }
        List<CollectionAssignee> collectors = collectionTaskMapper.selectCollectorOptions(projectId)
                .stream()
                .map(this::assignee)
                .toList();
        return new CollectionOptions(projects, collectors);
    }

    @Override
    @Transactional
    public void linkDataset(long id, long fileId, Access access) {
        requireManage(access);
        CollectionTaskSummaryRow task = requireAccessible(id, access);
        if (!"SUBMITTED".equals(task.getStatus())) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "Collection data can only be linked after the task reaches submitted status"
            );
        }
        Dataset dataset = datasetMapper.selectById(fileId);
        if (dataset == null
                || Boolean.TRUE.equals(dataset.getDeleted())
                || !"READY".equals(dataset.getMetadataStatus())
                || !task.getProjectId().equals(dataset.getProjectId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Ready project dataset not found");
        }
        collectionTaskMapper.linkDataset(id, fileId, access.userId());
    }

    @Override
    @Transactional
    public void unlinkDataset(long id, long fileId, Access access) {
        if (!access.unlinkData() && !access.administrator()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Collection data unlink permission is required");
        }
        requireAccessible(id, access);
        if (collectionTaskMapper.unlinkDataset(id, fileId) == 0) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Linked collection data not found");
        }
    }

    private CollectionTaskDetail detail(CollectionTaskSummaryRow row, Access access) {
        List<CollectionAssignee> assignees = collectionTaskMapper.selectAssignees(row.getId())
                .stream()
                .map(this::assignee)
                .toList();
        List<CollectionStep> steps = collectionTaskMapper.selectSteps(row.getId())
                .stream()
                .map(this::step)
                .toList();
        List<CollectionDataset> datasets = collectionTaskMapper.selectDatasets(row.getId())
                .stream()
                .map(this::dataset)
                .toList();
        boolean editable = canEdit(row, access) && !"SUBMITTED".equals(row.getStatus());
        return new CollectionTaskDetail(
                summary(row),
                row.getNotes(),
                row.getInitialScene(),
                Boolean.TRUE.equals(row.getRemoteOperationEnabled()),
                assignees,
                steps,
                datasets,
                allowedTransitions(row.getStatus(), access),
                editable && access.manage(),
                editable && access.manage(),
                access.unlinkData() || access.administrator()
        );
    }

    private void validateRequest(CollectionTaskRequest request, Access access) {
        if (Boolean.TRUE.equals(request.remoteOperationEnabled())) {
            throw new BusinessException(
                    ErrorCode.INVALID_ARGUMENT,
                    "The selected project has not enabled remote operation"
            );
        }
        boolean projectAllowed = collectionTaskMapper.selectProjectOptions(
                access.userId(),
                access.administrator()
        ).stream().anyMatch(project -> project.getId().equals(request.projectId()));
        if (!projectAllowed) {
            throw new BusinessException(
                    ErrorCode.FORBIDDEN,
                    "You do not have permission to create collection tasks in this project"
            );
        }
        Set<Long> assigneeIds = new LinkedHashSet<>(request.assigneeIds());
        if (collectionTaskMapper.countEligibleCollectors(request.projectId(), assigneeIds)
                != assigneeIds.size()) {
            throw new BusinessException(
                    ErrorCode.INVALID_ARGUMENT,
                    "One or more collectors are not active members of the selected project"
            );
        }
        for (CollectionStepRequest step : normalizedSteps(request.steps())) {
            if (!StringUtils.hasText(step.actionName()) && !StringUtils.hasText(step.notes())) {
                throw new BusinessException(
                        ErrorCode.INVALID_ARGUMENT,
                        "Each collection step requires an action or notes"
                );
            }
            if (StringUtils.hasText(step.objectName())
                    && (!StringUtils.hasText(step.actionName()) || !step.actionName().contains("{A}"))) {
                throw new BusinessException(
                        ErrorCode.INVALID_ARGUMENT,
                        "A collection step object requires an action containing {A}"
                );
            }
            if (StringUtils.hasText(step.targetName())
                    && (!StringUtils.hasText(step.actionName()) || !step.actionName().contains("{B}"))) {
                throw new BusinessException(
                        ErrorCode.INVALID_ARGUMENT,
                        "A collection step target requires an action containing {B}"
                );
            }
        }
    }

    private void requireUniqueName(String name, Long excludeId) {
        if (collectionTaskMapper.countByName(name.trim(), excludeId) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "Collection task name already exists");
        }
    }

    private void replaceChildren(long taskId, CollectionTaskRequest request) {
        collectionTaskMapper.deleteAssignees(taskId);
        collectionTaskMapper.deleteSteps(taskId);
        collectionTaskMapper.insertAssignees(taskId, new LinkedHashSet<>(request.assigneeIds()));
        List<CollectionStepRequest> steps = normalizedSteps(request.steps());
        if (!steps.isEmpty()) {
            collectionTaskMapper.insertSteps(taskId, steps);
        }
    }

    private List<CollectionStepRequest> normalizedSteps(List<CollectionStepRequest> steps) {
        if (steps == null) {
            return List.of();
        }
        return steps.stream()
                .map(step -> new CollectionStepRequest(
                        normalize(step.actionName()),
                        normalize(step.objectName()),
                        normalize(step.targetName()),
                        normalize(step.notes())
                ))
                .filter(step -> StringUtils.hasText(step.actionName())
                        || StringUtils.hasText(step.objectName())
                        || StringUtils.hasText(step.targetName())
                        || StringUtils.hasText(step.notes()))
                .toList();
    }

    private void apply(CollectionTask task, CollectionTaskRequest request) {
        task.setName(request.name().trim());
        task.setProjectId(request.projectId());
        task.setTargetCount(request.targetCount());
        task.setAverageDurationSeconds(request.averageDurationSeconds());
        task.setNotes(normalize(request.notes()));
        task.setInitialScene(normalize(request.initialScene()));
        task.setRemoteOperationEnabled(request.remoteOperationEnabled());
    }

    private CollectionTaskSummaryRow requireAccessible(long id, Access access) {
        CollectionTaskSummaryRow task = collectionTaskMapper.selectAccessibleById(
                id,
                access.userId(),
                access.administrator()
        );
        if (task == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Collection task not found");
        }
        return task;
    }

    private void requireManage(Access access) {
        if (!access.manage() && !access.administrator()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Collection task management permission is required");
        }
    }

    private void requireEditable(CollectionTaskSummaryRow task, Access access) {
        if (!canEdit(task, access)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "You cannot edit this collection task");
        }
    }

    private boolean canEdit(CollectionTaskSummaryRow task, Access access) {
        if (access.administrator()) {
            return true;
        }
        if (!task.getCreatedBy().equals(access.userId())) {
            return false;
        }
        String projectRole = projectMemberMapper.selectActiveRole(task.getProjectId(), access.userId());
        return "PROJECT_ADMIN".equals(projectRole) || "PROJECT_MANAGER".equals(projectRole);
    }

    private List<String> allowedTransitions(String status, Access access) {
        List<String> targets = new ArrayList<>();
        switch (status) {
            case "PENDING" -> addIf(targets, "WORKING", access.manage() || access.administrator());
            case "WORKING" -> addIf(targets, "REVIEW_PENDING", access.manage() || access.administrator());
            case "REVIEW_PENDING" -> {
                addIf(targets, "APPROVED", access.review() || access.administrator());
                addIf(targets, "REJECTED", access.review() || access.administrator());
                addIf(targets, "WORKING", access.manage() || access.administrator());
            }
            case "REJECTED" -> {
                addIf(targets, "REVIEW_PENDING", access.manage() || access.administrator());
                addIf(targets, "APPROVED", access.review() || access.administrator());
            }
            case "APPROVED" -> {
                addIf(targets, "REJECTED", access.review() || access.administrator());
                addIf(targets, "SUBMITTED", access.submit() || access.administrator());
            }
            default -> {
                // SUBMITTED is terminal.
            }
        }
        return List.copyOf(targets);
    }

    private void addIf(List<String> targets, String status, boolean allowed) {
        if (allowed) {
            targets.add(status);
        }
    }

    private CollectionTaskSummary summary(CollectionTaskSummaryRow row) {
        return new CollectionTaskSummary(
                row.getId(),
                row.getName(),
                row.getProjectId(),
                row.getProjectName(),
                row.getTargetCount(),
                row.getAverageDurationSeconds(),
                row.getCollectedCount(),
                row.getLatestFileName(),
                row.getLatestFileAt(),
                parseStringList(row.getAssignees()),
                parseStringList(row.getActions()),
                row.getStatus(),
                row.getCreatedBy(),
                row.getCreatedAt(),
                row.getUpdatedAt()
        );
    }

    private List<String> parseStringList(String json) {
        try {
            return objectMapper.readerForListOf(String.class).readValue(json == null ? "[]" : json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not decode collection task aggregate", exception);
        }
    }

    private CollectionAssignee assignee(AssigneeRow row) {
        return new CollectionAssignee(row.getId(), row.getUsername(), row.getDisplayName());
    }

    private CollectionAssignee assignee(OptionRow row) {
        return new CollectionAssignee(row.getId(), row.getUsername(), row.getDisplayName());
    }

    private CollectionStep step(StepRow row) {
        return new CollectionStep(
                row.getId(),
                row.getSequenceNo(),
                row.getActionName(),
                row.getObjectName(),
                row.getTargetName(),
                row.getNotes()
        );
    }

    private CollectionDataset dataset(DatasetRow row) {
        return new CollectionDataset(
                row.getId(),
                row.getName(),
                row.getSizeBytes(),
                row.getStatus(),
                row.getUploadedAt()
        );
    }

    private String normalizeStatus(String status) {
        String normalized = normalize(status);
        if (normalized != null && !STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Unknown collection task status");
        }
        return normalized;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}

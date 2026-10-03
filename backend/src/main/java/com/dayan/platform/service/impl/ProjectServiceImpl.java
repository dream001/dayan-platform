package com.dayan.platform.service.impl;

import com.dayan.platform.common.api.ErrorCode;
import com.dayan.platform.common.exception.BusinessException;
import com.dayan.platform.dto.ProjectDtos.ProjectMemberRequest;
import com.dayan.platform.dto.ProjectDtos.ProjectRequest;
import com.dayan.platform.model.Project;
import com.dayan.platform.model.UserAccount;
import com.dayan.platform.repository.mapper.ProjectMapper;
import com.dayan.platform.repository.mapper.ProjectMemberMapper;
import com.dayan.platform.repository.mapper.UserAccountMapper;
import com.dayan.platform.repository.query.ProjectMemberRow;
import com.dayan.platform.repository.query.ProjectMetricsRow;
import com.dayan.platform.repository.query.ProjectSummaryRow;
import com.dayan.platform.service.ProjectService;
import com.dayan.platform.vo.PageResponse;
import com.dayan.platform.vo.ProjectViews.ProjectDetail;
import com.dayan.platform.vo.ProjectViews.ProjectMember;
import com.dayan.platform.vo.ProjectViews.ProjectMetrics;
import com.dayan.platform.vo.ProjectViews.ProjectOverview;
import com.dayan.platform.vo.ProjectViews.ProjectSummary;
import com.dayan.platform.vo.ProjectViews.ProjectUserOption;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ProjectServiceImpl implements ProjectService {

    private static final Set<String> MANAGEMENT_ROLES =
            Set.of("PROJECT_ADMIN", "PROJECT_MANAGER");
    private static final Map<String, Set<String>> STATUS_TRANSITIONS = Map.of(
            "PLANNING", Set.of("ACTIVE", "ARCHIVED"),
            "ACTIVE", Set.of("SUSPENDED", "COMPLETED", "ARCHIVED"),
            "SUSPENDED", Set.of("ACTIVE", "ARCHIVED"),
            "COMPLETED", Set.of("ARCHIVED"),
            "ARCHIVED", Set.of()
    );

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final UserAccountMapper userAccountMapper;

    public ProjectServiceImpl(
            ProjectMapper projectMapper,
            ProjectMemberMapper projectMemberMapper,
            UserAccountMapper userAccountMapper
    ) {
        this.projectMapper = projectMapper;
        this.projectMemberMapper = projectMemberMapper;
        this.userAccountMapper = userAccountMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProjectSummary> page(
            int page,
            int size,
            String keyword,
            String status,
            String projectType,
            long userId,
            boolean platformAdmin
    ) {
        String normalizedKeyword = normalizeNullable(keyword);
        String normalizedStatus = normalizeEnum(status);
        String normalizedType = normalizeEnum(projectType);
        long total = projectMapper.countAccessible(
                userId,
                platformAdmin,
                normalizedKeyword,
                normalizedStatus,
                normalizedType
        );
        List<ProjectSummary> items = projectMapper.selectSummaryPage(
                userId,
                platformAdmin,
                normalizedKeyword,
                normalizedStatus,
                normalizedType,
                (long) (page - 1) * size,
                size
        ).stream().map(row -> summary(row, platformAdmin)).toList();
        return PageResponse.of(page, size, total, items);
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectOverview overview(long userId, boolean platformAdmin) {
        return new ProjectOverview(
                projectMapper.countAccessible(userId, platformAdmin, null, null, null),
                projectMapper.countAccessible(userId, platformAdmin, null, "ACTIVE", null),
                projectMapper.countAccessible(userId, platformAdmin, null, "PLANNING", null),
                projectMapper.countAccessible(userId, platformAdmin, null, "ARCHIVED", null)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectDetail detail(long id, long userId, boolean platformAdmin) {
        ProjectSummaryRow row = requireAccessible(id, userId, platformAdmin);
        return detail(row, platformAdmin);
    }

    @Override
    @Transactional
    public ProjectDetail create(ProjectRequest request, long operatorId) {
        validateProjectRequest(request);
        Project project = new Project();
        project.setStatus("PLANNING");
        project.setOwnerId(operatorId);
        apply(project, request);
        try {
            projectMapper.insert(project);
            projectMemberMapper.upsert(
                    project.getId(),
                    operatorId,
                    "PROJECT_ADMIN",
                    "FULL",
                    null,
                    null,
                    operatorId
            );
        } catch (DataIntegrityViolationException exception) {
            throw conflict("Project code already exists");
        }
        return detail(project.getId(), operatorId, true);
    }

    @Override
    @Transactional
    public ProjectDetail update(
            long id,
            ProjectRequest request,
            long operatorId,
            boolean platformAdmin
    ) {
        ProjectSummaryRow current = requireManageable(id, operatorId, platformAdmin);
        if ("ARCHIVED".equals(current.getStatus())) {
            throw conflict("Archived project cannot be edited");
        }
        validateProjectRequest(request);
        apply(current, request);
        current.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        try {
            projectMapper.updateById(current);
        } catch (DataIntegrityViolationException exception) {
            throw conflict("Project code already exists");
        }
        return detail(id, operatorId, platformAdmin);
    }

    @Override
    @Transactional
    public ProjectDetail changeStatus(
            long id,
            String status,
            long operatorId,
            boolean platformAdmin
    ) {
        ProjectSummaryRow project = requireManageable(id, operatorId, platformAdmin);
        String target = normalizeEnum(status);
        if (project.getStatus().equals(target)) {
            return detail(project, platformAdmin);
        }
        if (!STATUS_TRANSITIONS.getOrDefault(project.getStatus(), Set.of()).contains(target)) {
            throw conflict("Project status transition is not allowed");
        }
        project.setStatus(target);
        project.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        projectMapper.updateById(project);
        return detail(id, operatorId, platformAdmin);
    }

    @Override
    @Transactional
    public void delete(long id, long operatorId, boolean platformAdmin) {
        ProjectSummaryRow project = requireAccessible(id, operatorId, platformAdmin);
        boolean projectAdministrator = "PROJECT_ADMIN".equals(project.getCurrentRole());
        if (!platformAdmin && !projectAdministrator) {
            throw forbidden();
        }
        if (!"PLANNING".equals(project.getStatus())) {
            throw conflict("Only planning projects can be deleted");
        }
        if (projectMemberMapper.countAll(id) > 1) {
            throw conflict("Project still has assigned members");
        }
        projectMapper.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectMember> members(long id, long operatorId, boolean platformAdmin) {
        requireMemberAccess(id, operatorId, platformAdmin);
        return projectMemberMapper.selectMembers(id).stream().map(this::member).toList();
    }

    @Override
    @Transactional
    public ProjectMember saveMember(
            long id,
            ProjectMemberRequest request,
            long operatorId,
            boolean platformAdmin
    ) {
        ProjectSummaryRow project = requireManageable(id, operatorId, platformAdmin);
        validateMemberRequest(request);
        UserAccount user = userAccountMapper.selectById(request.userId());
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "User does not exist or is disabled");
        }
        if (project.getOwnerId().equals(request.userId())
                && (!"PROJECT_ADMIN".equals(request.role())
                || !"FULL".equals(request.dataAccessLevel())
                || request.validFrom() != null
                || request.validUntil() != null)) {
            throw conflict("Project owner must remain a project administrator with full access");
        }
        if (!platformAdmin
                && !"PROJECT_ADMIN".equals(project.getCurrentRole())
                && ("PROJECT_ADMIN".equals(request.role())
                || "PROJECT_ADMIN".equals(projectMemberMapper.selectRole(id, request.userId())))) {
            throw forbidden();
        }
        projectMemberMapper.upsert(
                id,
                request.userId(),
                request.role(),
                request.dataAccessLevel(),
                request.validFrom(),
                request.validUntil(),
                operatorId
        );
        return projectMemberMapper.selectMembers(id).stream()
                .filter(item -> item.getUserId().equals(request.userId()))
                .map(this::member)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Saved project member could not be loaded"));
    }

    @Override
    @Transactional
    public void removeMember(
            long id,
            long userId,
            long operatorId,
            boolean platformAdmin
    ) {
        ProjectSummaryRow project = requireManageable(id, operatorId, platformAdmin);
        if (project.getOwnerId().equals(userId)) {
            throw conflict("Project owner cannot be removed");
        }
        String removedRole = projectMemberMapper.selectRole(id, userId);
        if (removedRole == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Project member not found");
        }
        if ("PROJECT_ADMIN".equals(removedRole)
                && projectMemberMapper.countAdministrators(id) <= 1) {
            throw conflict("Project must retain at least one administrator");
        }
        if (!platformAdmin
                && !"PROJECT_ADMIN".equals(project.getCurrentRole())
                && "PROJECT_ADMIN".equals(removedRole)) {
            throw forbidden();
        }
        projectMemberMapper.delete(id, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectUserOption> userOptions(
            long id,
            String keyword,
            long operatorId,
            boolean platformAdmin
    ) {
        requireManageable(id, operatorId, platformAdmin);
        return projectMemberMapper.selectUserOptions(normalizeNullable(keyword)).stream()
                .map(user -> new ProjectUserOption(
                        user.getId(),
                        user.getUsername(),
                        user.getDisplayName()
                ))
                .toList();
    }

    private ProjectSummaryRow requireAccessible(long id, long userId, boolean platformAdmin) {
        ProjectSummaryRow project = projectMapper.selectSummaryById(id, userId);
        if (project == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Project not found");
        }
        if (!platformAdmin
                && !"PUBLIC".equals(project.getAccessLevel())
                && project.getCurrentRole() == null) {
            throw forbidden();
        }
        return project;
    }

    private ProjectSummaryRow requireManageable(long id, long userId, boolean platformAdmin) {
        ProjectSummaryRow project = requireAccessible(id, userId, platformAdmin);
        if (!platformAdmin && !MANAGEMENT_ROLES.contains(project.getCurrentRole())) {
            throw forbidden();
        }
        return project;
    }

    private ProjectSummaryRow requireMemberAccess(long id, long userId, boolean platformAdmin) {
        ProjectSummaryRow project = requireAccessible(id, userId, platformAdmin);
        if (!platformAdmin && project.getCurrentRole() == null) {
            throw forbidden();
        }
        return project;
    }

    private void validateProjectRequest(ProjectRequest request) {
        if (request.startDate() != null
                && request.endDate() != null
                && request.startDate().isAfter(request.endDate())) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Project end date precedes start date");
        }
        String provider = request.storageProvider().trim().toUpperCase(Locale.ROOT);
        if (!provider.matches("[A-Z0-9_-]{1,32}")) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Storage provider format is invalid");
        }
    }

    private void validateMemberRequest(ProjectMemberRequest request) {
        if (request.validFrom() != null
                && request.validUntil() != null
                && !request.validFrom().isBefore(request.validUntil())) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Member validity range is invalid");
        }
    }

    private void apply(Project project, ProjectRequest request) {
        project.setCode(request.code().trim().toUpperCase(Locale.ROOT));
        project.setName(request.name().trim());
        project.setDescription(normalizeNullable(request.description()));
        project.setProjectType(request.projectType());
        project.setAccessLevel(request.accessLevel());
        project.setStorageProvider(request.storageProvider().trim().toUpperCase(Locale.ROOT));
        project.setStorageQuotaBytes(request.storageQuotaBytes());
        project.setStartDate(request.startDate());
        project.setEndDate(request.endDate());
        project.setAnnotationGuideline(normalizeNullable(request.annotationGuideline()));
        project.setQualityThreshold(request.qualityThreshold());
        project.setReviewMode(request.reviewMode());
        project.setNotificationEnabled(request.notificationEnabled());
    }

    private ProjectDetail detail(ProjectSummaryRow row, boolean platformAdmin) {
        return new ProjectDetail(
                summary(row, platformAdmin),
                row.getStorageProvider(),
                row.getAnnotationGuideline(),
                row.getQualityThreshold(),
                row.getReviewMode(),
                Boolean.TRUE.equals(row.getNotificationEnabled()),
                row.getOwnerId(),
                row.getOwnerName(),
                row.getCreatedAt(),
                metrics(projectMapper.selectMetrics(row.getId()))
        );
    }

    private ProjectMetrics metrics(ProjectMetricsRow row) {
        long annotationTasks = value(row.getAnnotationTaskCount());
        long collectionTasks = value(row.getCollectionTaskCount());
        long totalTasks = annotationTasks + collectionTasks;
        long completedTasks = value(row.getCompletedTaskCount());
        BigDecimal completionRate = totalTasks == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(completedTasks)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(totalTasks), 2, RoundingMode.HALF_UP);
        return new ProjectMetrics(
                value(row.getDatasetCount()),
                value(row.getVideoCount()),
                value(row.getAudioCount()),
                value(row.getMcapCount()),
                value(row.getStorageUsedBytes()),
                annotationTasks,
                collectionTasks,
                completedTasks,
                completionRate,
                row.getQualityRate() == null ? BigDecimal.ZERO : row.getQualityRate(),
                value(row.getActiveMemberCount())
        );
    }

    private long value(Long value) {
        return value == null ? 0 : value;
    }

    private ProjectSummary summary(ProjectSummaryRow row, boolean platformAdmin) {
        boolean manager = platformAdmin
                || row.getCurrentRole() != null && MANAGEMENT_ROLES.contains(row.getCurrentRole());
        boolean administrator = platformAdmin || "PROJECT_ADMIN".equals(row.getCurrentRole());
        long memberCount = row.getMemberCount() == null ? 0 : row.getMemberCount();
        return new ProjectSummary(
                row.getId(),
                row.getCode(),
                row.getName(),
                row.getDescription(),
                row.getProjectType(),
                row.getAccessLevel(),
                row.getStatus(),
                row.getStorageQuotaBytes(),
                row.getStartDate(),
                row.getEndDate(),
                memberCount,
                row.getCurrentRole(),
                manager && !"ARCHIVED".equals(row.getStatus()),
                manager,
                administrator && "PLANNING".equals(row.getStatus()) && memberCount <= 1,
                row.getUpdatedAt()
        );
    }

    private ProjectMember member(ProjectMemberRow row) {
        return new ProjectMember(
                row.getUserId(),
                row.getUsername(),
                row.getDisplayName(),
                row.getRole(),
                row.getDataAccessLevel(),
                row.getValidFrom(),
                row.getValidUntil(),
                Boolean.TRUE.equals(row.getActive())
        );
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeEnum(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private BusinessException conflict(String message) {
        return new BusinessException(ErrorCode.CONFLICT, message);
    }

    private BusinessException forbidden() {
        return new BusinessException(ErrorCode.FORBIDDEN, "Project access denied");
    }
}

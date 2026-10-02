package com.dayan.platform.controller;

import com.dayan.platform.audit.Audited;
import com.dayan.platform.dto.AnnotationTaskDtos.BatchAnnotationRequest;
import com.dayan.platform.dto.AnnotationTaskDtos.CreateRequest;
import com.dayan.platform.dto.AnnotationTaskDtos.DatasetReviewRequest;
import com.dayan.platform.dto.AnnotationTaskDtos.StatusRequest;
import com.dayan.platform.dto.AnnotationTaskDtos.UpdateRequest;
import com.dayan.platform.service.AnnotationTaskService;
import com.dayan.platform.vo.AnnotationTaskViews.StatusCounts;
import com.dayan.platform.vo.AnnotationTaskViews.TaskDetail;
import com.dayan.platform.vo.AnnotationTaskViews.TaskOptions;
import com.dayan.platform.vo.AnnotationTaskViews.TaskSummary;
import com.dayan.platform.vo.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("${app.api.base-path}/data/annotation-tasks")
public class AnnotationTaskController {

    private static final String MANAGE = "data:annotate:task:manage";
    private static final String EXECUTE = "data:annotate:task:execute";
    private static final String REVIEW = "data:annotate:task:review";

    private final AnnotationTaskService taskService;

    public AnnotationTaskController(AnnotationTaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('data:annotate:task:view')")
    public PageResponse<TaskSummary> page(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long annotatorId,
            @RequestParam(required = false) Long reviewerId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdDate
    ) {
        return taskService.page(
                page,
                size,
                keyword,
                status,
                projectId,
                annotatorId,
                reviewerId,
                createdDate,
                userId(jwt),
                has(authentication, MANAGE)
        );
    }

    @GetMapping("/counts")
    @PreAuthorize("hasAuthority('data:annotate:task:view')")
    public StatusCounts counts(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return taskService.counts(userId(jwt), has(authentication, MANAGE));
    }

    @GetMapping("/options")
    @PreAuthorize("hasAuthority('data:annotate:task:view')")
    public TaskOptions options(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @RequestParam(required = false) Long projectId
    ) {
        return taskService.options(projectId, userId(jwt), has(authentication, MANAGE));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('data:annotate:task:view')")
    public TaskDetail detail(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @PathVariable long id
    ) {
        return taskService.detail(
                id,
                userId(jwt),
                has(authentication, MANAGE),
                has(authentication, EXECUTE),
                has(authentication, REVIEW)
        );
    }

    @PostMapping
    @PreAuthorize("hasAuthority('data:annotate:task:manage')")
    @Audited(module = "ANNOTATION_TASK", action = "CREATE", targetType = "TASK")
    public List<TaskDetail> create(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @Valid @RequestBody CreateRequest request
    ) {
        return taskService.create(
                request,
                userId(jwt),
                true,
                has(authentication, EXECUTE),
                has(authentication, REVIEW)
        );
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('data:annotate:task:manage')")
    @Audited(module = "ANNOTATION_TASK", action = "UPDATE", targetType = "TASK", targetId = "#id")
    public TaskDetail update(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @PathVariable long id,
            @Valid @RequestBody UpdateRequest request
    ) {
        return taskService.update(
                id,
                request,
                userId(jwt),
                true,
                has(authentication, EXECUTE),
                has(authentication, REVIEW)
        );
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyAuthority('data:annotate:task:manage', 'data:annotate:task:execute', "
            + "'data:annotate:task:review')")
    @Audited(
            module = "ANNOTATION_TASK",
            action = "CHANGE_STATUS",
            targetType = "TASK",
            targetId = "#id"
    )
    public TaskDetail changeStatus(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @PathVariable long id,
            @Valid @RequestBody StatusRequest request
    ) {
        return taskService.changeStatus(
                id,
                request,
                userId(jwt),
                has(authentication, MANAGE),
                has(authentication, EXECUTE),
                has(authentication, REVIEW)
        );
    }

    @PatchMapping("/{id}/datasets/{relationId}/review")
    @PreAuthorize("hasAnyAuthority('data:annotate:task:manage', 'data:annotate:task:execute', "
            + "'data:annotate:task:review')")
    @Audited(
            module = "ANNOTATION_TASK",
            action = "REVIEW_DATASET",
            targetType = "TASK_DATASET",
            targetId = "#relationId"
    )
    public TaskDetail reviewDataset(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @PathVariable long id,
            @PathVariable long relationId,
            @Valid @RequestBody DatasetReviewRequest request
    ) {
        return taskService.reviewDataset(
                id,
                relationId,
                request,
                userId(jwt),
                has(authentication, MANAGE),
                has(authentication, EXECUTE),
                has(authentication, REVIEW)
        );
    }

    @PatchMapping("/{id}/batch-annotation")
    @PreAuthorize("hasAnyAuthority('data:annotate:task:manage', 'data:annotate:task:execute')")
    @Audited(
            module = "ANNOTATION_TASK",
            action = "BATCH_ANNOTATE",
            targetType = "TASK",
            targetId = "#id"
    )
    public TaskDetail batchAnnotate(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @PathVariable long id,
            @Valid @RequestBody BatchAnnotationRequest request
    ) {
        return taskService.batchAnnotate(
                id,
                request,
                userId(jwt),
                has(authentication, MANAGE),
                has(authentication, EXECUTE),
                has(authentication, REVIEW)
        );
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('data:annotate:task:manage')")
    @Audited(module = "ANNOTATION_TASK", action = "DELETE", targetType = "TASK", targetId = "#id")
    public void delete(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @PathVariable long id
    ) {
        taskService.delete(id, userId(jwt), has(authentication, MANAGE));
    }

    private long userId(Jwt jwt) {
        return ((Number) jwt.getClaim("uid")).longValue();
    }

    private boolean has(Authentication authentication, String permission) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> permission.equals(authority.getAuthority()));
    }
}

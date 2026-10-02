package com.dayan.platform.controller;

import com.dayan.platform.audit.Audited;
import com.dayan.platform.dto.CollectionTaskDtos.CollectionStatusRequest;
import com.dayan.platform.dto.CollectionTaskDtos.CollectionTaskRequest;
import com.dayan.platform.service.CollectionTaskService;
import com.dayan.platform.service.CollectionTaskService.Access;
import com.dayan.platform.vo.CollectionTaskViews.CollectionOptions;
import com.dayan.platform.vo.CollectionTaskViews.CollectionStatusCounts;
import com.dayan.platform.vo.CollectionTaskViews.CollectionTaskDetail;
import com.dayan.platform.vo.CollectionTaskViews.CollectionTaskSummary;
import com.dayan.platform.vo.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
@RequestMapping("${app.api.base-path}/collections")
public class CollectionTaskController {

    private final CollectionTaskService collectionTaskService;

    public CollectionTaskController(CollectionTaskService collectionTaskService) {
        this.collectionTaskService = collectionTaskService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('data:collect:task:view')")
    public PageResponse<CollectionTaskSummary> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long collectorId,
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return collectionTaskService.page(
                page,
                size,
                keyword,
                collectorId,
                status,
                access(jwt, authentication)
        );
    }

    @GetMapping("/status-counts")
    @PreAuthorize("hasAuthority('data:collect:task:view')")
    public CollectionStatusCounts statusCounts(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return collectionTaskService.statusCounts(access(jwt, authentication));
    }

    @GetMapping("/options")
    @PreAuthorize("hasAuthority('data:collect:task:manage')")
    public CollectionOptions options(
            @RequestParam(required = false) Long projectId,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return collectionTaskService.options(projectId, access(jwt, authentication));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('data:collect:task:view')")
    public CollectionTaskDetail detail(
            @PathVariable long id,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return collectionTaskService.detail(id, access(jwt, authentication));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('data:collect:task:manage')")
    @Audited(
            module = "COLLECTION_TASK",
            action = "CREATE",
            targetType = "COLLECTION_TASK",
            targetId = "#result == null ? #request.name() : #result.summary().id()"
    )
    public CollectionTaskDetail create(
            @Valid @RequestBody CollectionTaskRequest request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return collectionTaskService.create(request, access(jwt, authentication));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('data:collect:task:manage')")
    @Audited(module = "COLLECTION_TASK", action = "UPDATE", targetType = "COLLECTION_TASK", targetId = "#id")
    public CollectionTaskDetail update(
            @PathVariable long id,
            @Valid @RequestBody CollectionTaskRequest request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return collectionTaskService.update(id, request, access(jwt, authentication));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('data:collect:task:manage')")
    @Audited(
            module = "COLLECTION_TASK",
            action = "CHANGE_STATUS",
            targetType = "COLLECTION_TASK",
            targetId = "#id"
    )
    public CollectionTaskDetail changeStatus(
            @PathVariable long id,
            @Valid @RequestBody CollectionStatusRequest request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return collectionTaskService.changeStatus(id, request, access(jwt, authentication));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('data:collect:task:manage')")
    @Audited(module = "COLLECTION_TASK", action = "DELETE", targetType = "COLLECTION_TASK", targetId = "#id")
    public void delete(
            @PathVariable long id,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        collectionTaskService.delete(id, access(jwt, authentication));
    }

    @PostMapping("/{id}/datasets/{fileId}")
    @PreAuthorize("hasAuthority('data:collect:task:manage')")
    @Audited(module = "COLLECTION_TASK", action = "LINK_DATA", targetType = "COLLECTION_TASK", targetId = "#id")
    public void linkDataset(
            @PathVariable long id,
            @PathVariable long fileId,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        collectionTaskService.linkDataset(id, fileId, access(jwt, authentication));
    }

    @DeleteMapping("/{id}/datasets/{fileId}")
    @PreAuthorize("hasAuthority('data:collect:task:unlink-data')")
    @Audited(module = "COLLECTION_TASK", action = "UNLINK_DATA", targetType = "COLLECTION_TASK", targetId = "#id")
    public void unlinkDataset(
            @PathVariable long id,
            @PathVariable long fileId,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        collectionTaskService.unlinkDataset(id, fileId, access(jwt, authentication));
    }

    private Access access(Jwt jwt, Authentication authentication) {
        return new Access(
                ((Number) jwt.getClaim("uid")).longValue(),
                has(authentication, "data:collect:task:admin"),
                has(authentication, "data:collect:task:manage"),
                has(authentication, "data:collect:task:review"),
                has(authentication, "data:collect:task:submit"),
                has(authentication, "data:collect:task:unlink-data")
        );
    }

    private boolean has(Authentication authentication, String authority) {
        return authentication.getAuthorities().stream()
                .anyMatch(granted -> authority.equals(granted.getAuthority()));
    }
}

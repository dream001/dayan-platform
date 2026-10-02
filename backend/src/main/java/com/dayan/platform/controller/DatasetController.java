package com.dayan.platform.controller;

import com.dayan.platform.audit.Audited;
import com.dayan.platform.dto.DatasetDtos.AnnotateRequest;
import com.dayan.platform.dto.DatasetDtos.BatchIds;
import com.dayan.platform.dto.DatasetDtos.DatasetRegisterRequest;
import com.dayan.platform.dto.DatasetDtos.ImportRequest;
import com.dayan.platform.dto.DatasetDtos.RenameRequest;
import com.dayan.platform.dto.DatasetDtos.RobotRequest;
import com.dayan.platform.dto.DatasetDtos.TagBatchRequest;
import com.dayan.platform.dto.DatasetFilter;
import com.dayan.platform.repository.query.OptionRow;
import com.dayan.platform.service.DatasetService;
import com.dayan.platform.vo.DatasetViews.DatasetDetail;
import com.dayan.platform.vo.DatasetViews.DatasetStats;
import com.dayan.platform.vo.DatasetViews.DatasetView;
import com.dayan.platform.vo.DatasetViews.ItemResult;
import com.dayan.platform.vo.DatasetViews.TaskBrief;
import com.dayan.platform.vo.PageResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${app.api.base-path}/datasets")
public class DatasetController {

    private static final String ADMIN_PERMISSION = "basic:project:update";

    private final DatasetService datasetService;

    public DatasetController(DatasetService datasetService) {
        this.datasetService = datasetService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('data:manage:view')")
    public PageResponse<DatasetView> page(
            @Valid @ModelAttribute DatasetFilter filter,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        applyActor(filter, jwt, authentication);
        return datasetService.page(filter);
    }

    @GetMapping("/storage-total")
    @PreAuthorize("hasAuthority('data:manage:view')")
    public long storageTotal(
            @Valid @ModelAttribute DatasetFilter filter,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        applyActor(filter, jwt, authentication);
        return datasetService.storageTotal(filter);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('data:manage:view')")
    public DatasetDetail detail(
            @PathVariable long id,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return datasetService.detail(id, userId(jwt), isAdmin(authentication));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('data:dataset:create')")
    @Audited(module = "DATASET", action = "REGISTER", targetType = "DATASET", targetId = "#result.id()")
    public DatasetView register(
            @Valid @RequestBody DatasetRegisterRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return datasetService.register(request, userId(jwt));
    }

    @PostMapping("/batch/rename")
    @PreAuthorize("hasAuthority('data:dataset:update')")
    @Audited(module = "DATASET", action = "RENAME", targetType = "DATASET")
    public List<ItemResult> rename(
            @Valid @RequestBody RenameRequest request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return datasetService.rename(
                request.items(),
                userId(jwt),
                isAdmin(authentication)
        );
    }

    @PostMapping("/batch/annotate")
    @PreAuthorize("hasAuthority('data:dataset:annotate')")
    @Audited(module = "DATASET", action = "ANNOTATE", targetType = "TASK", targetId = "#result.id()")
    public TaskBrief annotate(
            @Valid @RequestBody AnnotateRequest request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return datasetService.annotate(request, userId(jwt), isAdmin(authentication));
    }

    @PostMapping("/batch/tags/add")
    @PreAuthorize("hasAuthority('data:dataset:annotate')")
    @Audited(module = "DATASET", action = "ADD_TAGS", targetType = "DATASET")
    public void addTags(
            @Valid @RequestBody TagBatchRequest request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        datasetService.addTags(request, userId(jwt), isAdmin(authentication));
    }

    @PostMapping("/batch/tags/remove")
    @PreAuthorize("hasAuthority('data:dataset:annotate')")
    @Audited(module = "DATASET", action = "REMOVE_TAGS", targetType = "DATASET")
    public void removeTags(
            @Valid @RequestBody TagBatchRequest request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        datasetService.removeTags(request, userId(jwt), isAdmin(authentication));
    }

    @PostMapping("/batch/refresh")
    @PreAuthorize("hasAuthority('data:dataset:preprocess')")
    @Audited(module = "DATASET", action = "REFRESH_METADATA", targetType = "DATASET")
    public List<ItemResult> refresh(
            @Valid @RequestBody BatchIds request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return datasetService.refreshMetadata(request.ids(), userId(jwt), isAdmin(authentication));
    }

    @PostMapping("/batch/delete")
    @PreAuthorize("hasAuthority('data:dataset:delete')")
    @Audited(module = "DATASET", action = "SOFT_DELETE", targetType = "DATASET")
    public void delete(
            @Valid @RequestBody BatchIds request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        datasetService.softDelete(request.ids(), userId(jwt), isAdmin(authentication));
    }

    @PostMapping("/batch/import")
    @PreAuthorize("hasAuthority('data:dataset:import')")
    @Audited(module = "DATASET", action = "IMPORT", targetType = "PROJECT")
    public void importToProject(
            @Valid @RequestBody ImportRequest request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        datasetService.importToProject(request, userId(jwt), isAdmin(authentication));
    }

    @PostMapping("/batch/robot")
    @PreAuthorize("hasAuthority('data:dataset:robot')")
    @Audited(module = "DATASET", action = "ASSIGN_ROBOT", targetType = "DATASET")
    public void assignRobot(
            @Valid @RequestBody RobotRequest request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        datasetService.assignRobot(request, userId(jwt), isAdmin(authentication));
    }

    @PostMapping("/batch/stats")
    @PreAuthorize("hasAuthority('data:manage:view')")
    public DatasetStats stats(
            @Valid @RequestBody BatchIds request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return datasetService.stats(request.ids(), userId(jwt), isAdmin(authentication));
    }

    @GetMapping("/trash")
    @PreAuthorize("hasAuthority('data:dataset:delete')")
    public List<DatasetView> trash(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return datasetService.trash(userId(jwt), isAdmin(authentication));
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAuthority('data:dataset:delete')")
    @Audited(module = "DATASET", action = "RESTORE", targetType = "DATASET", targetId = "#id")
    public void restore(
            @PathVariable long id,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        datasetService.restore(id, userId(jwt), isAdmin(authentication));
    }

    @GetMapping("/options/robots")
    @PreAuthorize("hasAuthority('data:manage:view')")
    public List<String> robotOptions() {
        return datasetService.robotOptions();
    }

    @GetMapping("/options/tags")
    @PreAuthorize("hasAuthority('data:manage:view')")
    public List<String> tagOptions() {
        return datasetService.tagOptions();
    }

    @GetMapping("/options/users")
    @PreAuthorize("hasAuthority('data:manage:view')")
    public List<OptionRow> userOptions() {
        return datasetService.userOptions();
    }

    private void applyActor(DatasetFilter filter, Jwt jwt, Authentication authentication) {
        filter.setCurrentUserId(userId(jwt));
        filter.setAdmin(isAdmin(authentication));
    }

    private long userId(Jwt jwt) {
        return ((Number) jwt.getClaim("uid")).longValue();
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> ADMIN_PERMISSION.equals(authority.getAuthority()));
    }
}

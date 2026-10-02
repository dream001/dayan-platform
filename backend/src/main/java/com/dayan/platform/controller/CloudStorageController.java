package com.dayan.platform.controller;

import com.dayan.platform.audit.Audited;
import com.dayan.platform.dto.CloudStorageDtos.CloudStorageRequest;
import com.dayan.platform.service.CloudStorageService;
import com.dayan.platform.vo.CloudStorageViews.CloudStorageOverview;
import com.dayan.platform.vo.CloudStorageViews.CloudStorageView;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${app.api.base-path}/basic/storages")
public class CloudStorageController {

    private final CloudStorageService storageService;

    public CloudStorageController(CloudStorageService storageService) {
        this.storageService = storageService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('basic:storage:view')")
    public List<CloudStorageView> list() {
        return storageService.list();
    }

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('basic:storage:view')")
    public CloudStorageOverview overview() {
        return storageService.overview();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('basic:storage:create')")
    @Audited(
            module = "CLOUD_STORAGE",
            action = "CREATE",
            targetType = "CLOUD_STORAGE",
            targetId = "#result == null ? #request.storageKey() : #result.id()"
    )
    public CloudStorageView create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CloudStorageRequest request
    ) {
        return storageService.create(request, ((Number) jwt.getClaim("uid")).longValue());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('basic:storage:update')")
    @Audited(module = "CLOUD_STORAGE", action = "UPDATE", targetType = "CLOUD_STORAGE", targetId = "#id")
    public CloudStorageView update(
            @PathVariable long id,
            @Valid @RequestBody CloudStorageRequest request
    ) {
        return storageService.update(id, request);
    }

    @PostMapping("/{id}/test")
    @PreAuthorize("hasAuthority('basic:storage:test')")
    @Audited(module = "CLOUD_STORAGE", action = "TEST", targetType = "CLOUD_STORAGE", targetId = "#id")
    public CloudStorageView test(@PathVariable long id) {
        return storageService.test(id);
    }

    @PatchMapping("/{id}/default")
    @PreAuthorize("hasAuthority('basic:storage:default')")
    @Audited(
            module = "CLOUD_STORAGE",
            action = "SET_DEFAULT",
            targetType = "CLOUD_STORAGE",
            targetId = "#id"
    )
    public CloudStorageView setDefault(@PathVariable long id) {
        return storageService.setDefault(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('basic:storage:delete')")
    @Audited(module = "CLOUD_STORAGE", action = "DELETE", targetType = "CLOUD_STORAGE", targetId = "#id")
    public void delete(@PathVariable long id) {
        storageService.delete(id);
    }
}

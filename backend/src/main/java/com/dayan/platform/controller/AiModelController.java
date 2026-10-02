package com.dayan.platform.controller;

import com.dayan.platform.audit.Audited;
import com.dayan.platform.dto.AiModelDtos.ModelRequest;
import com.dayan.platform.dto.AiModelDtos.ModelStatusRequest;
import com.dayan.platform.dto.AiModelDtos.ModelType;
import com.dayan.platform.service.AiModelService;
import com.dayan.platform.vo.AiModelViews.ModelSummary;
import com.dayan.platform.vo.AiModelViews.ModelTestResult;
import com.dayan.platform.vo.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("${app.api.base-path}/basic/models")
public class AiModelController {

    private final AiModelService modelService;

    public AiModelController(AiModelService modelService) {
        this.modelService = modelService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('basic:model:view')")
    public PageResponse<ModelSummary> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ModelType modelType,
            @RequestParam(required = false) Boolean enabled
    ) {
        return modelService.page(page, size, keyword, modelType, enabled);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('basic:model:view')")
    public ModelSummary get(@PathVariable long id) {
        return modelService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('basic:model:create')")
    @Audited(
            module = "MODEL",
            action = "CREATE",
            targetType = "AI_MODEL",
            targetId = "#result == null ? #request.name() : #result.id()"
    )
    public ModelSummary create(@Valid @RequestBody ModelRequest request) {
        return modelService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('basic:model:update')")
    @Audited(module = "MODEL", action = "UPDATE", targetType = "AI_MODEL", targetId = "#id")
    public ModelSummary update(
            @PathVariable long id,
            @Valid @RequestBody ModelRequest request
    ) {
        return modelService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('basic:model:update')")
    @Audited(module = "MODEL", action = "CHANGE_STATUS", targetType = "AI_MODEL", targetId = "#id")
    public ModelSummary changeStatus(
            @PathVariable long id,
            @Valid @RequestBody ModelStatusRequest request
    ) {
        return modelService.changeStatus(id, request.enabled());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('basic:model:delete')")
    @Audited(module = "MODEL", action = "DELETE", targetType = "AI_MODEL", targetId = "#id")
    public void delete(@PathVariable long id) {
        modelService.delete(id);
    }

    @PostMapping("/{id}/test")
    @PreAuthorize("hasAuthority('basic:model:test')")
    @Audited(module = "MODEL", action = "TEST", targetType = "AI_MODEL", targetId = "#id")
    public ModelTestResult test(@PathVariable long id) {
        return modelService.test(id);
    }
}

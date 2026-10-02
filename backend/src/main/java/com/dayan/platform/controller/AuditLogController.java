package com.dayan.platform.controller;

import com.dayan.platform.dto.AuditLogQuery;
import com.dayan.platform.service.AuditLogService;
import com.dayan.platform.vo.AuditViews.OperationLogDetail;
import com.dayan.platform.vo.AuditViews.OperationLogSummary;
import com.dayan.platform.vo.PageResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${app.api.base-path}/audit/logs")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('audit:log:view')")
    public PageResponse<OperationLogSummary> page(@Valid @ModelAttribute AuditLogQuery query) {
        return auditLogService.page(query);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('audit:log:detail')")
    public OperationLogDetail detail(@PathVariable long id) {
        return auditLogService.detail(id);
    }
}

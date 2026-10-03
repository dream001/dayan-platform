package com.dayan.platform.controller;

import com.dayan.platform.audit.Audited;
import com.dayan.platform.common.api.RawResponse;
import com.dayan.platform.dto.DataExportDtos.CreateRequest;
import com.dayan.platform.dto.DataExportDtos.QuotaRequest;
import com.dayan.platform.service.DataExportService;
import com.dayan.platform.service.DataExportService.Download;
import com.dayan.platform.vo.DataExportViews.DatasetOption;
import com.dayan.platform.vo.DataExportViews.QuotaUserView;
import com.dayan.platform.vo.DataExportViews.QuotaView;
import com.dayan.platform.vo.DataExportViews.TaskDetail;
import com.dayan.platform.vo.DataExportViews.TaskView;
import com.dayan.platform.vo.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.core.io.InputStreamResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("${app.api.base-path}/data/exports")
public class DataExportController {

    private static final String ADMIN_PERMISSION = "basic:project:update";

    private final DataExportService exportService;

    public DataExportController(DataExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping("/datasets")
    @PreAuthorize("hasAuthority('data:export:view')")
    public List<DatasetOption> datasets(
            @RequestParam(required = false) @Positive Long projectId,
            @RequestParam(required = false) @Positive Long collectorId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) String keyword,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return exportService.options(
                projectId,
                collectorId,
                from,
                to,
                keyword,
                userId(jwt),
                isAdmin(authentication)
        );
    }

    @PostMapping
    @PreAuthorize("hasAuthority('data:export:create')")
    @Audited(module = "DATA_EXPORT", action = "CREATE", targetType = "EXPORT_TASK",
            targetId = "#result.id()")
    public TaskView create(
            @Valid @RequestBody CreateRequest request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return exportService.create(request, userId(jwt), isAdmin(authentication));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('data:export:view')")
    public PageResponse<TaskView> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String format,
            @RequestParam(required = false)
            @Pattern(regexp = "PENDING|PROCESSING|COMPLETED|FAILED") String status,
            @RequestParam(required = false) String keyword,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return exportService.page(
                page,
                size,
                format,
                status,
                keyword,
                userId(jwt),
                isAdmin(authentication)
        );
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('data:export:view')")
    public TaskDetail detail(
            @PathVariable long id,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return exportService.detail(id, userId(jwt), isAdmin(authentication));
    }

    @GetMapping("/quota/current")
    @PreAuthorize("hasAuthority('data:export:view')")
    public QuotaView quota(@AuthenticationPrincipal Jwt jwt) {
        return exportService.quota(userId(jwt));
    }

    @GetMapping("/quotas")
    @PreAuthorize("hasAuthority('data:export:quota')")
    public List<QuotaUserView> quotas() {
        return exportService.quotas();
    }

    @PatchMapping("/quotas/{userId}")
    @PreAuthorize("hasAuthority('data:export:quota')")
    @Audited(module = "DATA_EXPORT", action = "UPDATE_QUOTA", targetType = "USER",
            targetId = "#userId")
    public void updateQuota(
            @PathVariable long userId,
            @Valid @RequestBody QuotaRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        exportService.updateQuota(userId, request.quotaLimit(), userId(jwt));
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("hasAuthority('data:export:download')")
    @RawResponse
    public ResponseEntity<InputStreamResource> download(
            @PathVariable long id,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        Download download = exportService.download(id, userId(jwt), isAdmin(authentication));
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(safeMediaType(download.contentType()))
                .contentLength(download.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(new InputStreamResource(download.inputStream()));
    }

    private long userId(Jwt jwt) {
        return ((Number) jwt.getClaim("uid")).longValue();
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> ADMIN_PERMISSION.equals(authority.getAuthority()));
    }

    private MediaType safeMediaType(String contentType) {
        try {
            return MediaType.parseMediaType(contentType);
        } catch (IllegalArgumentException exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}

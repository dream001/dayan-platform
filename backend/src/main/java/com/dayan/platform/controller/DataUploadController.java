package com.dayan.platform.controller;

import com.dayan.platform.audit.Audited;
import com.dayan.platform.dto.DataUploadDtos.CreateSessionRequest;
import com.dayan.platform.service.DataUploadService;
import com.dayan.platform.vo.DataUploadViews.DatasetView;
import com.dayan.platform.vo.DataUploadViews.UploadOptions;
import com.dayan.platform.vo.DataUploadViews.UploadSessionView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("${app.api.base-path}/data/uploads")
public class DataUploadController {

    private final DataUploadService service;

    public DataUploadController(DataUploadService service) {
        this.service = service;
    }

    @GetMapping("/options")
    @PreAuthorize("hasAuthority('data:upload:view')")
    public UploadOptions options(@AuthenticationPrincipal Jwt jwt) {
        return service.options(userId(jwt));
    }

    @PostMapping(value = "/direct", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('data:upload:create')")
    @Audited(module = "DATA_UPLOAD", action = "DIRECT_UPLOAD", targetType = "DATASET")
    public DatasetView uploadDirect(
            @RequestParam @Positive long projectId,
            @RequestParam @NotBlank String storageKey,
            @RequestParam @NotBlank String dataType,
            @RequestParam @NotBlank String sourceFingerprint,
            @RequestParam(required = false) String robotType,
            @RequestParam(required = false)
            @DecimalMin(value = "0.001")
            @Digits(integer = 9, fraction = 3) BigDecimal durationSeconds,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return service.uploadDirect(
                projectId, storageKey, dataType, sourceFingerprint, robotType,
                durationSeconds, file, userId(jwt)
        );
    }

    @PostMapping("/sessions")
    @PreAuthorize("hasAuthority('data:upload:create')")
    public UploadSessionView createSession(
            @Valid @org.springframework.web.bind.annotation.RequestBody CreateSessionRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return service.createSession(request, userId(jwt));
    }

    @GetMapping("/sessions/{id}")
    @PreAuthorize("hasAuthority('data:upload:create')")
    public UploadSessionView session(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return service.session(id, userId(jwt));
    }

    @PutMapping(value = "/sessions/{id}/parts/{partNumber}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('data:upload:create')")
    public void uploadPart(
            @PathVariable UUID id,
            @PathVariable int partNumber,
            @RequestPart("chunk") MultipartFile chunk,
            @AuthenticationPrincipal Jwt jwt
    ) {
        service.uploadPart(id, partNumber, chunk, userId(jwt));
    }

    @PostMapping("/sessions/{id}/pause")
    @PreAuthorize("hasAuthority('data:upload:create')")
    public void pause(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        service.pause(id, userId(jwt));
    }

    @PostMapping("/sessions/{id}/resume")
    @PreAuthorize("hasAuthority('data:upload:create')")
    public UploadSessionView resume(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return service.resume(id, userId(jwt));
    }

    @PostMapping("/sessions/{id}/complete")
    @PreAuthorize("hasAuthority('data:upload:create')")
    @Audited(module = "DATA_UPLOAD", action = "MULTIPART_COMPLETE", targetType = "DATASET")
    public DatasetView complete(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return service.complete(id, userId(jwt));
    }

    @DeleteMapping("/sessions/{id}")
    @PreAuthorize("hasAuthority('data:upload:create')")
    public void cancel(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        service.cancel(id, userId(jwt));
    }

    private long userId(Jwt jwt) {
        return ((Number) jwt.getClaim("uid")).longValue();
    }
}

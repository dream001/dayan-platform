package com.dayan.platform.controller;

import com.dayan.platform.audit.Audited;
import com.dayan.platform.common.api.RawResponse;
import com.dayan.platform.dto.DictionaryDtos.DictionaryBatchDeleteRequest;
import com.dayan.platform.dto.DictionaryDtos.DictionaryBatchRequest;
import com.dayan.platform.dto.DictionaryDtos.DictionaryRequest;
import com.dayan.platform.dto.DictionaryDtos.DictionaryScope;
import com.dayan.platform.dto.DictionaryDtos.DictionarySort;
import com.dayan.platform.dto.DictionaryDtos.DictionaryType;
import com.dayan.platform.dto.DictionaryDtos.SortDirection;
import com.dayan.platform.service.DictionaryService;
import com.dayan.platform.vo.DictionaryViews.DictionaryBatchResult;
import com.dayan.platform.vo.DictionaryViews.DictionaryExport;
import com.dayan.platform.vo.DictionaryViews.DictionaryItemView;
import com.dayan.platform.vo.DictionaryViews.DictionaryOverview;
import com.dayan.platform.vo.DictionaryViews.DictionaryProjectOption;
import com.dayan.platform.vo.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("${app.api.base-path}/data/dictionaries")
public class DictionaryController {

    private final DictionaryService dictionaryService;

    public DictionaryController(DictionaryService dictionaryService) {
        this.dictionaryService = dictionaryService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('data:dict:view')")
    public PageResponse<DictionaryItemView> page(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size,
            @RequestParam DictionaryType type,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) DictionaryScope scope,
            @RequestParam(required = false) Long projectId,
            @RequestParam(defaultValue = "CREATED_AT") DictionarySort sort,
            @RequestParam(defaultValue = "DESC") SortDirection direction
    ) {
        return dictionaryService.page(
                page,
                size,
                type,
                keyword,
                scope,
                projectId,
                sort,
                direction,
                userId(jwt),
                isPlatformAdmin(authentication)
        );
    }

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('data:dict:view')")
    public DictionaryOverview overview(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return dictionaryService.overview(userId(jwt), isPlatformAdmin(authentication));
    }

    @GetMapping("/project-options")
    @PreAuthorize("hasAuthority('data:dict:view')")
    public List<DictionaryProjectOption> projectOptions(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return dictionaryService.projectOptions(userId(jwt), isPlatformAdmin(authentication));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('data:dict:manage')")
    @Audited(
            module = "DICTIONARY",
            action = "CREATE",
            targetType = "DICTIONARY_ITEM",
            targetId = "#result == null ? #request.englishText() : #result.id()"
    )
    public DictionaryItemView create(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @Valid @RequestBody DictionaryRequest request
    ) {
        return dictionaryService.create(request, userId(jwt), isPlatformAdmin(authentication));
    }

    @PostMapping("/batch")
    @PreAuthorize("hasAuthority('data:dict:manage')")
    @Audited(module = "DICTIONARY", action = "BATCH_CREATE", targetType = "DICTIONARY_ITEM")
    public DictionaryBatchResult batchCreate(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @Valid @RequestBody DictionaryBatchRequest request
    ) {
        return dictionaryService.batchCreate(
                request,
                userId(jwt),
                isPlatformAdmin(authentication)
        );
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('data:dict:manage')")
    @Audited(module = "DICTIONARY", action = "UPDATE", targetType = "DICTIONARY_ITEM", targetId = "#id")
    public DictionaryItemView update(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @PathVariable long id,
            @Valid @RequestBody DictionaryRequest request
    ) {
        return dictionaryService.update(
                id,
                request,
                userId(jwt),
                isPlatformAdmin(authentication)
        );
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('data:dict:manage')")
    @Audited(module = "DICTIONARY", action = "DELETE", targetType = "DICTIONARY_ITEM", targetId = "#id")
    public void delete(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @PathVariable long id
    ) {
        dictionaryService.delete(id, userId(jwt), isPlatformAdmin(authentication));
    }

    @PostMapping("/batch-delete")
    @PreAuthorize("hasAuthority('data:dict:manage')")
    @Audited(module = "DICTIONARY", action = "BATCH_DELETE", targetType = "DICTIONARY_ITEM")
    public DictionaryBatchResult batchDelete(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @Valid @RequestBody DictionaryBatchDeleteRequest request
    ) {
        return dictionaryService.batchDelete(
                request.ids(),
                userId(jwt),
                isPlatformAdmin(authentication)
        );
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('data:dict:view')")
    @RawResponse
    public ResponseEntity<byte[]> export(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @RequestParam DictionaryType type
    ) {
        DictionaryExport export = dictionaryService.export(
                type,
                userId(jwt),
                isPlatformAdmin(authentication)
        );
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(export.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .contentLength(export.content().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(export.content());
    }

    private long userId(Jwt jwt) {
        return ((Number) jwt.getClaim("uid")).longValue();
    }

    private boolean isPlatformAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "data:dict:global".equals(authority.getAuthority()));
    }
}

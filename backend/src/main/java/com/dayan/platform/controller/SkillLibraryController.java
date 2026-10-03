package com.dayan.platform.controller;

import com.dayan.platform.audit.Audited;
import com.dayan.platform.dto.SkillLibraryDtos.SkillAssetRequest;
import com.dayan.platform.service.SkillLibraryService;
import com.dayan.platform.vo.PageResponse;
import com.dayan.platform.vo.SkillLibraryViews.ProjectOption;
import com.dayan.platform.vo.SkillLibraryViews.SkillLibrary;
import com.dayan.platform.vo.SkillLibraryViews.SkillSample;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("${app.api.base-path}/data/skills")
@PreAuthorize("hasAuthority('data:skill:view')")
public class SkillLibraryController {

    private static final Set<String> PLATFORM_ADMIN_PERMISSIONS = Set.of(
            "basic:project:create",
            "basic:project:update",
            "basic:project:delete",
            "basic:project:member:manage"
    );

    private final SkillLibraryService service;

    public SkillLibraryController(SkillLibraryService service) {
        this.service = service;
    }

    @GetMapping("/projects")
    public List<ProjectOption> projects(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return service.projectOptions(userId(jwt), isPlatformAdmin(authentication));
    }

    @GetMapping
    public SkillLibrary skills(
            @RequestParam(required = false) @Positive Long projectId,
            @RequestParam(defaultValue = "en-US") String locale,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return service.skills(
                projectId,
                locale,
                userId(jwt),
                isPlatformAdmin(authentication)
        );
    }

    @GetMapping("/{skillKey}/samples")
    public PageResponse<SkillSample> samples(
            @PathVariable @Size(min = 1, max = 128) String skillKey,
            @RequestParam(required = false) @Positive Long projectId,
            @RequestParam(defaultValue = "COLOR") String mediaType,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "30") @Min(1) @Max(30) int size,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return service.samples(
                skillKey,
                projectId,
                mediaType,
                page,
                size,
                userId(jwt),
                isPlatformAdmin(authentication)
        );
    }

    @PutMapping("/catalog/{skillKey}")
    @PreAuthorize("hasAuthority('data:skill:manage')")
    @Audited(module = "SKILL_LIBRARY", action = "SAVE", targetType = "SKILL",
            targetId = "#skillKey")
    public void save(
            @PathVariable @Size(min = 2, max = 128) String skillKey,
            @Valid @RequestBody SkillAssetRequest request
    ) {
        service.save(skillKey, request);
    }

    private long userId(Jwt jwt) {
        return ((Number) jwt.getClaim("uid")).longValue();
    }

    private boolean isPlatformAdmin(Authentication authentication) {
        Set<String> permissions = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .collect(java.util.stream.Collectors.toSet());
        return permissions.containsAll(PLATFORM_ADMIN_PERMISSIONS);
    }
}

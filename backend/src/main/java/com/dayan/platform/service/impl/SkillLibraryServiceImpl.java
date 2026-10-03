package com.dayan.platform.service.impl;

import com.dayan.platform.common.api.ErrorCode;
import com.dayan.platform.common.exception.BusinessException;
import com.dayan.platform.dto.SkillLibraryDtos.SkillAssetRequest;
import com.dayan.platform.repository.mapper.SkillLibraryMapper;
import com.dayan.platform.repository.query.SkillLibraryRows.SkillSampleRow;
import com.dayan.platform.repository.query.SkillLibraryRows.SkillSummaryRow;
import com.dayan.platform.service.FileService;
import com.dayan.platform.service.SkillLibraryService;
import com.dayan.platform.vo.FileViews.PreviewView;
import com.dayan.platform.vo.PageResponse;
import com.dayan.platform.vo.SkillLibraryViews.ProjectOption;
import com.dayan.platform.vo.SkillLibraryViews.SkillLibrary;
import com.dayan.platform.vo.SkillLibraryViews.SkillSample;
import com.dayan.platform.vo.SkillLibraryViews.SkillSummary;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SkillLibraryServiceImpl implements SkillLibraryService {

    private static final Logger log = LoggerFactory.getLogger(SkillLibraryServiceImpl.class);

    private final SkillLibraryMapper mapper;
    private final FileService fileService;

    public SkillLibraryServiceImpl(SkillLibraryMapper mapper, FileService fileService) {
        this.mapper = mapper;
        this.fileService = fileService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectOption> projectOptions(long userId, boolean platformAdmin) {
        return mapper.selectProjectOptions(userId, platformAdmin);
    }

    @Override
    @Transactional(readOnly = true)
    public SkillLibrary skills(
            Long projectId,
            String locale,
            long userId,
            boolean platformAdmin
    ) {
        requireProject(projectId, userId, platformAdmin);
        boolean localized = locale != null && locale.toLowerCase(Locale.ROOT).startsWith("zh");
        List<SkillSummaryRow> rows = mapper.selectSummaries(
                projectId,
                localized,
                userId,
                platformAdmin
        );
        Map<String, MutableSummary> summaries = new LinkedHashMap<>();
        for (SkillSummaryRow row : rows) {
            String normalized = row.skillKey.toLowerCase(Locale.ROOT);
            MutableSummary summary = summaries.computeIfAbsent(
                    normalized,
                    key -> new MutableSummary(row)
            );
            summary.annotationCount += row.annotationCount;
            summary.projectCount += row.projectCount;
        }
        for (SkillSampleRow row : mapper.selectSummarySamples(projectId, userId, platformAdmin)) {
            MutableSummary summary = summaries.get(row.skillKey.toLowerCase(Locale.ROOT));
            if (summary != null) {
                summary.samples.add(toSample(row));
            }
        }
        List<SkillSummary> skills = summaries.values().stream()
                .map(MutableSummary::toView)
                .toList();
        long annotationCount = skills.stream().mapToLong(SkillSummary::annotationCount).sum();
        return new SkillLibrary(skills.size(), annotationCount, skills);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SkillSample> samples(
            String skillKey,
            Long projectId,
            String mediaType,
            int page,
            int size,
            long userId,
            boolean platformAdmin
    ) {
        requireProject(projectId, userId, platformAdmin);
        String normalizedSkill = skillKey == null ? "" : skillKey.trim();
        if (normalizedSkill.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Skill must not be blank");
        }
        String normalizedMedia = mediaType == null
                ? "COLOR"
                : mediaType.trim().toUpperCase(Locale.ROOT);
        if (!List.of("COLOR", "DEPTH").contains(normalizedMedia)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Unsupported media type");
        }
        long total = mapper.countDetailSamples(
                normalizedSkill,
                projectId,
                normalizedMedia,
                userId,
                platformAdmin
        );
        long offset = (long) (page - 1) * size;
        List<SkillSample> samples = mapper.selectDetailSamples(
                normalizedSkill,
                projectId,
                normalizedMedia,
                userId,
                platformAdmin,
                size,
                offset
        ).stream().map(this::toSample).toList();
        return PageResponse.of(page, size, total, samples);
    }

    @Override
    @Transactional
    public void save(String skillKey, SkillAssetRequest request) {
        String normalizedKey = skillKey == null
                ? ""
                : skillKey.trim().toLowerCase(Locale.ROOT);
        if (!normalizedKey.matches("[a-z0-9][a-z0-9._-]{1,127}")) {
            throw new BusinessException(
                    ErrorCode.INVALID_ARGUMENT,
                    "Skill key must contain only letters, numbers, dots, underscores, or hyphens"
            );
        }
        String tags = request.tags().stream()
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .map(tag -> tag.replace(",", ""))
                .distinct()
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        mapper.saveSkill(
                normalizedKey,
                request.nameZh().trim(),
                request.nameEn().trim(),
                normalize(request.description()),
                request.category(),
                request.difficulty(),
                request.status(),
                request.currentVersion(),
                normalize(request.usageScene()),
                tags,
                request.template()
        );
        mapper.insertVersion(
                normalizedKey,
                request.currentVersion(),
                "PUBLISHED".equals(request.status()),
                "Catalog metadata updated"
        );
    }

    private SkillSample toSample(SkillSampleRow row) {
        PreviewView preview = null;
        try {
            preview = fileService.preview(row.fileId);
        } catch (RuntimeException exception) {
            log.warn("Unable to create skill sample preview: fileId={}", row.fileId);
        }
        return new SkillSample(
                row.annotationId,
                row.datasetId,
                row.description,
                row.projectName,
                row.dataType,
                row.contentType,
                row.mediaType,
                preview == null ? null : preview.url(),
                preview == null ? null : preview.expiresAt(),
                row.createdAt
        );
    }

    private void requireProject(Long projectId, long userId, boolean platformAdmin) {
        if (
                projectId != null
                && mapper.countAccessibleProject(projectId, userId, platformAdmin) == 0
        ) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Project access denied");
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static final class MutableSummary {
        private final String key;
        private final String name;
        private final String description;
        private final String category;
        private final String difficulty;
        private final String status;
        private final String currentVersion;
        private final String usageScene;
        private final List<String> tags;
        private long annotationCount;
        private long projectCount;
        private final long recentUsageCount;
        private final long versionCount;
        private final long dependencyCount;
        private final double qualityRate;
        private final List<SkillSample> samples = new ArrayList<>();

        private MutableSummary(SkillSummaryRow row) {
            this.key = row.skillKey;
            this.name = row.displayName;
            this.description = row.description;
            this.category = row.category;
            this.difficulty = row.difficulty;
            this.status = row.status;
            this.currentVersion = row.currentVersion;
            this.usageScene = row.usageScene;
            this.tags = row.tagsCsv == null || row.tagsCsv.isBlank()
                    ? List.of()
                    : List.of(row.tagsCsv.split(","));
            this.recentUsageCount = row.recentUsageCount;
            this.versionCount = row.versionCount;
            this.dependencyCount = row.dependencyCount;
            this.qualityRate = row.qualityRate;
        }

        private SkillSummary toView() {
            return new SkillSummary(
                    key,
                    name,
                    description,
                    category,
                    difficulty,
                    status,
                    currentVersion,
                    usageScene,
                    tags,
                    annotationCount,
                    projectCount,
                    recentUsageCount,
                    versionCount,
                    dependencyCount,
                    qualityRate,
                    samples
            );
        }
    }
}

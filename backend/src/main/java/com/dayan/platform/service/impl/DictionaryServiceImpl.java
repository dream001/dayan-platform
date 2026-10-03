package com.dayan.platform.service.impl;

import com.dayan.platform.common.api.ErrorCode;
import com.dayan.platform.common.exception.BusinessException;
import com.dayan.platform.dto.DictionaryDtos.DictionaryBatchRequest;
import com.dayan.platform.dto.DictionaryDtos.DictionaryRequest;
import com.dayan.platform.dto.DictionaryDtos.DictionaryScope;
import com.dayan.platform.dto.DictionaryDtos.DictionarySort;
import com.dayan.platform.dto.DictionaryDtos.DictionaryType;
import com.dayan.platform.dto.DictionaryDtos.SortDirection;
import com.dayan.platform.model.DictionaryItem;
import com.dayan.platform.repository.mapper.DictionaryItemMapper;
import com.dayan.platform.repository.query.DictionaryRows.DictionaryItemRow;
import com.dayan.platform.repository.query.DictionaryRows.DictionaryTypeCountRow;
import com.dayan.platform.service.DictionaryService;
import com.dayan.platform.vo.DictionaryViews.DictionaryBatchResult;
import com.dayan.platform.vo.DictionaryViews.DictionaryExport;
import com.dayan.platform.vo.DictionaryViews.DictionaryItemView;
import com.dayan.platform.vo.DictionaryViews.DictionaryOverview;
import com.dayan.platform.vo.DictionaryViews.DictionaryProjectOption;
import com.dayan.platform.vo.PageResponse;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DictionaryServiceImpl implements DictionaryService {

    private static final int MAX_BATCH_SIZE = 200;
    private static final Pattern HAN_CHARACTER = Pattern.compile("\\p{IsHan}");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([AB])}");

    private final DictionaryItemMapper dictionaryMapper;

    public DictionaryServiceImpl(DictionaryItemMapper dictionaryMapper) {
        this.dictionaryMapper = dictionaryMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DictionaryItemView> page(
            int page,
            int size,
            DictionaryType type,
            String keyword,
            DictionaryScope scope,
            Long projectId,
            DictionarySort sort,
            SortDirection direction,
            long userId,
            boolean platformAdmin
    ) {
        requireVisibleType(type, platformAdmin);
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        String scopeValue = scope == null ? null : scope.name();
        long total = dictionaryMapper.countPage(
                userId,
                platformAdmin,
                type.name(),
                normalizedKeyword,
                scopeValue,
                projectId
        );
        List<DictionaryItemView> items = dictionaryMapper.selectPage(
                userId,
                platformAdmin,
                type.name(),
                normalizedKeyword,
                scopeValue,
                projectId,
                orderColumn(sort),
                direction == null ? SortDirection.DESC.name() : direction.name(),
                (long) (page - 1) * size,
                size
        ).stream().map(row -> view(row, userId, platformAdmin)).toList();
        return PageResponse.of(page, size, total, items);
    }

    @Override
    @Transactional(readOnly = true)
    public DictionaryOverview overview(long userId, boolean platformAdmin) {
        Map<String, Long> counts = new HashMap<>();
        for (DictionaryType type : DictionaryType.values()) {
            if (type != DictionaryType.TAG_CATEGORY || platformAdmin) {
                counts.put(type.name(), 0L);
            }
        }
        for (DictionaryTypeCountRow row : dictionaryMapper.selectTypeCounts(userId, platformAdmin)) {
            counts.put(row.getDictionaryType(), row.getCount());
        }
        return new DictionaryOverview(Map.copyOf(counts), platformAdmin);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DictionaryProjectOption> projectOptions(long userId, boolean platformAdmin) {
        return dictionaryMapper.selectProjectOptions(userId, platformAdmin);
    }

    @Override
    @Transactional
    public DictionaryItemView create(
            DictionaryRequest request,
            long userId,
            boolean platformAdmin
    ) {
        DictionaryItem item = new DictionaryItem();
        item.setCreatorId(userId);
        apply(item, request, userId, platformAdmin, false);
        insert(item);
        return view(dictionaryMapper.selectRowById(item.getId()), userId, platformAdmin);
    }

    @Override
    @Transactional
    public DictionaryBatchResult batchCreate(
            DictionaryBatchRequest request,
            long userId,
            boolean platformAdmin
    ) {
        List<DictionaryRequest> rows = parseBatch(request);
        for (DictionaryRequest row : rows) {
            DictionaryItem item = new DictionaryItem();
            item.setCreatorId(userId);
            apply(item, row, userId, platformAdmin, false);
            insert(item);
        }
        return new DictionaryBatchResult(rows.size(), 0, 0);
    }

    @Override
    @Transactional
    public DictionaryItemView update(
            long id,
            DictionaryRequest request,
            long userId,
            boolean platformAdmin
    ) {
        DictionaryItemRow existing = requireAccessible(id, userId, platformAdmin);
        requireMutable(existing, userId, platformAdmin);
        if ("GLOBAL".equals(existing.getScope()) && request.scope() != DictionaryScope.GLOBAL) {
            throw conflict("Global dictionary scope cannot be changed");
        }
        apply(existing, request, userId, platformAdmin, true);
        existing.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        try {
            dictionaryMapper.updateById(existing);
        } catch (DataIntegrityViolationException exception) {
            throw conflict("A dictionary item with the same English text already exists");
        }
        return view(dictionaryMapper.selectRowById(id), userId, platformAdmin);
    }

    @Override
    @Transactional
    public void delete(long id, long userId, boolean platformAdmin) {
        DictionaryItemRow item = requireAccessible(id, userId, platformAdmin);
        requireMutable(item, userId, platformAdmin);
        dictionaryMapper.deleteById(id);
    }

    @Override
    @Transactional
    public DictionaryBatchResult batchDelete(Set<Long> ids, long userId, boolean platformAdmin) {
        Set<Long> normalizedIds = new LinkedHashSet<>(ids);
        int deleted = 0;
        int skipped = 0;
        for (Long id : normalizedIds) {
            DictionaryItemRow item = dictionaryMapper.selectAccessibleById(
                    id,
                    userId,
                    platformAdmin
            );
            if (item == null || !canModify(item, userId, platformAdmin)) {
                skipped++;
                continue;
            }
            dictionaryMapper.deleteById(id);
            deleted++;
        }
        return new DictionaryBatchResult(0, deleted, skipped);
    }

    @Override
    @Transactional(readOnly = true)
    public DictionaryExport export(DictionaryType type, long userId, boolean platformAdmin) {
        requireVisibleType(type, platformAdmin);
        StringBuilder csv = new StringBuilder("\uFEFFenglish,chinese,japanese,scope,project,use_count,created_at\n");
        for (DictionaryItemRow row : dictionaryMapper.selectForExport(
                userId,
                platformAdmin,
                type.name()
        )) {
            appendCsv(csv, row.getEnglishText());
            appendCsv(csv, row.getChineseText());
            appendCsv(csv, row.getJapaneseText());
            appendCsv(csv, row.getScope());
            appendCsv(csv, row.getProjectName());
            appendCsv(csv, String.valueOf(row.getUseCount()));
            appendCsv(csv, String.valueOf(row.getCreatedAt()));
            csv.setLength(csv.length() - 1);
            csv.append('\n');
        }
        return new DictionaryExport(
                "dictionary-" + type.name().toLowerCase() + ".csv",
                csv.toString().getBytes(StandardCharsets.UTF_8)
        );
    }

    private void apply(
            DictionaryItem item,
            DictionaryRequest request,
            long userId,
            boolean platformAdmin,
            boolean updating
    ) {
        requireVisibleType(request.dictionaryType(), platformAdmin);
        validateScope(request.scope(), request.projectId(), userId, platformAdmin);
        String english = request.englishText().trim();
        String chinese = request.chineseText().trim();
        String japanese = trimToNull(request.japaneseText());
        if (!StringUtils.hasText(english) || !StringUtils.hasText(chinese)) {
            throw invalid("Dictionary English and Chinese text are required");
        }
        if (HAN_CHARACTER.matcher(english).find()) {
            throw invalid("Dictionary English text must not contain Chinese characters");
        }
        if (request.dictionaryType() == DictionaryType.SKILL) {
            validateSkillPlaceholders(english, chinese);
        }
        Long excludedId = updating ? item.getId() : null;
        long duplicateCount = request.scope() == DictionaryScope.GLOBAL
                ? dictionaryMapper.countGlobalDuplicate(
                        request.dictionaryType().name(),
                        english,
                        excludedId
                )
                : dictionaryMapper.countCreatorDuplicate(
                        request.dictionaryType().name(),
                        english,
                        item.getCreatorId(),
                        excludedId
                );
        if (duplicateCount > 0) {
            throw conflict("A dictionary item with the same English text already exists");
        }
        item.setDictionaryType(request.dictionaryType().name());
        item.setScope(request.scope().name());
        item.setProjectId(request.scope() == DictionaryScope.PROJECT ? request.projectId() : null);
        item.setEnglishText(english);
        item.setChineseText(chinese);
        item.setJapaneseText(japanese);
    }

    private void validateScope(
            DictionaryScope scope,
            Long projectId,
            long userId,
            boolean platformAdmin
    ) {
        if (scope == DictionaryScope.GLOBAL && !platformAdmin) {
            throw forbidden("Only administrators can manage global dictionaries");
        }
        if (scope != DictionaryScope.PROJECT) {
            return;
        }
        if (projectId == null) {
            throw invalid("Project dictionaries require a project");
        }
        if (!platformAdmin && dictionaryMapper.countActiveProjectMembership(projectId, userId) == 0) {
            throw forbidden("Project membership is required to manage this dictionary");
        }
    }

    private void validateSkillPlaceholders(String english, String chinese) {
        if (!placeholderCounts(english).equals(placeholderCounts(chinese))) {
            throw invalid("Skill placeholders must match between English and Chinese text");
        }
    }

    private Map<String, Integer> placeholderCounts(String text) {
        Map<String, Integer> counts = new HashMap<>();
        Matcher matcher = PLACEHOLDER.matcher(text);
        while (matcher.find()) {
            String key = matcher.group(1);
            counts.merge(key, 1, Integer::sum);
        }
        return counts;
    }

    private List<DictionaryRequest> parseBatch(DictionaryBatchRequest request) {
        if (request.content().contains("，")) {
            throw invalid("Use an English comma or a tab to separate languages");
        }
        List<String> lines = request.content().lines()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
        if (lines.isEmpty() || lines.size() > MAX_BATCH_SIZE) {
            throw invalid("Batch input must contain between 1 and 200 rows");
        }
        return lines.stream().map(line -> {
            String delimiter = line.contains("\t") ? "\t" : ",";
            String[] values = line.split(delimiter, -1);
            if (values.length < 2 || values.length > 3) {
                throw invalid("Each batch row must contain English, Chinese, and optional Japanese text");
            }
            return new DictionaryRequest(
                    request.dictionaryType(),
                    request.scope(),
                    request.projectId(),
                    values[0].trim(),
                    values[1].trim(),
                    values.length == 3 ? values[2].trim() : null
            );
        }).toList();
    }

    private void insert(DictionaryItem item) {
        try {
            dictionaryMapper.insert(item);
        } catch (DataIntegrityViolationException exception) {
            throw conflict("A dictionary item with the same English text already exists");
        }
    }

    private DictionaryItemRow requireAccessible(long id, long userId, boolean platformAdmin) {
        DictionaryItemRow item = dictionaryMapper.selectAccessibleById(id, userId, platformAdmin);
        if (item == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Dictionary item not found");
        }
        return item;
    }

    private void requireMutable(DictionaryItemRow item, long userId, boolean platformAdmin) {
        if (!canModify(item, userId, platformAdmin)) {
            throw forbidden("Dictionary item cannot be modified by the current user");
        }
    }

    private boolean canModify(DictionaryItem item, long userId, boolean platformAdmin) {
        if (platformAdmin) {
            return true;
        }
        if (item.getCreatorId() == userId) {
            return true;
        }
        return "PROJECT".equals(item.getScope())
                && item.getProjectId() != null
                && dictionaryMapper.countProjectManagementMembership(item.getProjectId(), userId) > 0;
    }

    private DictionaryItemView view(DictionaryItemRow row, long userId, boolean platformAdmin) {
        boolean mutable = canModify(row, userId, platformAdmin);
        return new DictionaryItemView(
                row.getId(),
                row.getDictionaryType(),
                row.getScope(),
                row.getProjectId(),
                row.getProjectName(),
                row.getEnglishText(),
                row.getChineseText(),
                row.getJapaneseText(),
                row.getUseCount(),
                row.getCreatorId(),
                row.getCreatorName(),
                mutable,
                mutable,
                row.getCreatedAt(),
                row.getUpdatedAt()
        );
    }

    private void requireVisibleType(DictionaryType type, boolean platformAdmin) {
        if (type == DictionaryType.TAG_CATEGORY && !platformAdmin) {
            throw forbidden("Tag category dictionaries are only visible to administrators");
        }
    }

    private String orderColumn(DictionarySort sort) {
        if (sort == null) {
            return "d.created_at";
        }
        return switch (sort) {
            case USE_COUNT -> "d.use_count";
            case ENGLISH -> "lower(d.english_text)";
            case CHINESE -> "d.chinese_text";
            case JAPANESE -> "d.japanese_text";
            case CREATED_AT -> "d.created_at";
        };
    }

    private void appendCsv(StringBuilder csv, String value) {
        String safe = value == null ? "" : value.replace("\"", "\"\"");
        csv.append('"').append(safe).append('"').append(',');
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_ARGUMENT, message);
    }

    private BusinessException conflict(String message) {
        return new BusinessException(ErrorCode.CONFLICT, message);
    }

    private BusinessException forbidden(String message) {
        return new BusinessException(ErrorCode.FORBIDDEN, message);
    }

}

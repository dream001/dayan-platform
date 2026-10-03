package com.dayan.platform.vo;

import java.time.OffsetDateTime;
import java.util.Map;

public final class DictionaryViews {

    private DictionaryViews() {
    }

    public record DictionaryItemView(
            long id,
            String dictionaryType,
            String scope,
            Long projectId,
            String projectName,
            String englishText,
            String chineseText,
            String japaneseText,
            long useCount,
            long creatorId,
            String creatorName,
            boolean canEdit,
            boolean canDelete,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record DictionaryProjectOption(
            long id,
            String name
    ) {
    }

    public record DictionaryOverview(
            Map<String, Long> counts,
            boolean canManageGlobal
    ) {
    }

    public record DictionaryBatchResult(
            int created,
            int deleted,
            int skipped
    ) {
    }

    public record DictionaryExport(
            String fileName,
            byte[] content
    ) {
    }
}

package com.dayan.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

public final class DictionaryDtos {

    private DictionaryDtos() {
    }

    public enum DictionaryType {
        SKILL,
        OBJECT,
        TARGET,
        ADVERBIAL,
        INVALID,
        TAG,
        TAG_CATEGORY
    }

    public enum DictionaryScope {
        GLOBAL,
        SHARED,
        PROJECT
    }

    public enum DictionarySort {
        USE_COUNT,
        ENGLISH,
        CHINESE,
        JAPANESE,
        CREATED_AT
    }

    public enum SortDirection {
        ASC,
        DESC
    }

    public record DictionaryRequest(
            @NotNull DictionaryType dictionaryType,
            @NotNull DictionaryScope scope,
            Long projectId,
            @NotBlank @Size(max = 300) String englishText,
            @NotBlank @Size(max = 300) String chineseText,
            @Size(max = 300) String japaneseText
    ) {
    }

    public record DictionaryBatchRequest(
            @NotNull DictionaryType dictionaryType,
            @NotNull DictionaryScope scope,
            Long projectId,
            @NotBlank @Size(max = 30_000) String content
    ) {
    }

    public record DictionaryBatchDeleteRequest(
            @NotEmpty @Size(max = 500) Set<Long> ids
    ) {
    }
}

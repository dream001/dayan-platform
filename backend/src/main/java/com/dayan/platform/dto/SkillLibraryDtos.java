package com.dayan.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class SkillLibraryDtos {

    private SkillLibraryDtos() {
    }

    public record SkillAssetRequest(
            @NotBlank @Size(max = 128) String nameZh,
            @NotBlank @Size(max = 128) String nameEn,
            @Size(max = 1000) String description,
            @NotBlank @Pattern(regexp = "BASIC|COMPOSITE|PROFESSIONAL") String category,
            @NotBlank @Pattern(regexp = "BEGINNER|INTERMEDIATE|ADVANCED|EXPERT")
            String difficulty,
            @NotBlank @Pattern(regexp = "DRAFT|PUBLISHED|DEPRECATED") String status,
            @NotBlank @Pattern(regexp = "[0-9]+\\.[0-9]+\\.[0-9]+") String currentVersion,
            @Size(max = 200) String usageScene,
            @NotNull @Size(max = 20) List<@NotBlank @Size(max = 40) String> tags,
            @NotNull Boolean template
    ) {
    }
}

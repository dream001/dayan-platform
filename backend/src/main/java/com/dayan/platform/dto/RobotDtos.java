package com.dayan.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class RobotDtos {

    private RobotDtos() {
    }

    public enum RobotType {
        HUMANOID,
        MOBILE_MANIPULATOR,
        DESKTOP_ARM,
        MOBILE_BASE,
        QUADRUPED,
        INDUSTRIAL_ARM,
        OTHER
    }

    public enum ActionMappingSupport {
        SUPPORTED,
        COMING_SOON,
        UNSUPPORTED
    }

    public record RobotRequest(
            @NotBlank @Size(max = 255) String name,
            @Size(max = 1000) String iconUrl,
            @Positive Long iconFileId,
            @Size(max = 255) String titleZh,
            @Size(max = 255) String titleEn,
            @NotNull RobotType robotType,
            @NotNull ActionMappingSupport actionMappingSupport,
            String description,
            @Size(max = 100) String company,
            @Size(max = 1000) String introductionUrl
    ) {
    }
}

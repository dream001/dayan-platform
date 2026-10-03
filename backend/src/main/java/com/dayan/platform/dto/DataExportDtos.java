package com.dayan.platform.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class DataExportDtos {

    private DataExportDtos() {
    }

    public record CreateRequest(
            @NotBlank @Size(max = 255) String name,
            @NotBlank
            @Pattern(regexp = "LEROBOT|HDF5|MCAP|JSON|CSV|YOLO|COCO|VOC|"
                    + "TIME_ALIGNMENT|DROPPED_FRAME|MCAP_CHUNK")
            String format,
            @NotEmpty @Size(max = 100) List<@NotNull @Positive Long> datasetIds,
            @Pattern(regexp = "IMAGE|VIDEO") String mediaMode,
            @Min(1) @Max(240) Integer sampleRate,
            @Min(1) @Max(100) Integer chunkSize,
            @Pattern(regexp = "LATEST|V2_1") String version,
            Boolean strictMatch,
            Boolean blurFaces,
            @Size(max = 32) String annotationType
    ) {
    }

    public record QuotaRequest(@Min(0) int quotaLimit) {
    }
}

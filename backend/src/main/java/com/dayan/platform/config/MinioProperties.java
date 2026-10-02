package com.dayan.platform.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "app.storage")
public record MinioProperties(
        @NotBlank String endpoint,
        @NotBlank String publicEndpoint,
        @NotBlank String accessKey,
        @NotBlank String secretKey,
        @NotBlank String bucket,
        @NotNull DataSize maxFileSize,
        @NotEmpty List<@NotBlank String> allowedContentTypes,
        @NotNull Duration previewTtl
) {

    private static final Duration MAXIMUM_PRESIGN_TTL = Duration.ofDays(7);

    public MinioProperties {
        if (maxFileSize != null && maxFileSize.toBytes() <= 0) {
            throw new IllegalArgumentException("maxFileSize must be positive");
        }
        if (previewTtl != null
                && (previewTtl.isNegative()
                || previewTtl.isZero()
                || previewTtl.compareTo(MAXIMUM_PRESIGN_TTL) > 0)) {
            throw new IllegalArgumentException("previewTtl must be between 1 millisecond and 7 days");
        }
        if (allowedContentTypes != null) {
            allowedContentTypes = allowedContentTypes.stream()
                    .map(value -> value.trim().toLowerCase(Locale.ROOT))
                    .distinct()
                    .toList();
        }
    }
}

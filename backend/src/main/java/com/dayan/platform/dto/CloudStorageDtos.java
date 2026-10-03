package com.dayan.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class CloudStorageDtos {

    private CloudStorageDtos() {
    }

    public record CloudStorageRequest(
            @NotBlank
            @Size(max = 64)
            @Pattern(regexp = "^[a-z][a-z0-9-]*$", message = "storage key format is invalid")
            String storageKey,
            @NotBlank @Size(max = 120) String name,
            @NotBlank
            @Pattern(regexp = "TENCENT_COS|ALIYUN_OSS|VOLCENGINE_TOS|HUAWEI_OBS|AWS_S3|AZURE_BLOB|CLOUDFLARE_R2|MINIO")
            String provider,
            @NotBlank @Size(max = 500) String endpoint,
            @Size(max = 100) String region,
            @NotBlank @Size(max = 255) String bucket,
            @Size(max = 500) String accessKey,
            @Size(max = 1000) String secretKey,
            @NotNull Boolean enabled
    ) {
    }
}

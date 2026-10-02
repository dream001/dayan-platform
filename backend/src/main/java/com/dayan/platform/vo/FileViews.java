package com.dayan.platform.vo;

import java.time.OffsetDateTime;

public final class FileViews {

    private FileViews() {
    }

    public record FileView(
            long id,
            String originalName,
            String contentType,
            long sizeBytes,
            String etag,
            Long uploaderId,
            String status,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record PreviewView(String url, OffsetDateTime expiresAt) {
    }
}

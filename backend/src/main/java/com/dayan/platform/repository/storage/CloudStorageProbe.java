package com.dayan.platform.repository.storage;

public interface CloudStorageProbe {

    Usage probe(Connection connection);

    record Connection(
            String provider,
            String endpoint,
            String region,
            String bucket,
            String accessKey,
            String secretKey
    ) {
    }

    record Usage(long bytes, long objects) {
    }
}

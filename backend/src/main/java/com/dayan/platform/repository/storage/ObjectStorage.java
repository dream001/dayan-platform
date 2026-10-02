package com.dayan.platform.repository.storage;

import java.io.InputStream;
import java.time.Duration;

public interface ObjectStorage {

    void ensureBucket();

    String put(String objectKey, InputStream inputStream, long size, String contentType);

    StoredObject get(String objectKey);

    String presignGet(String objectKey, String responseContentDisposition, Duration ttl);

    void remove(String objectKey);

    record StoredObject(InputStream inputStream) {
    }
}

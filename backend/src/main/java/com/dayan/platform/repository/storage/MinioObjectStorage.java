package com.dayan.platform.repository.storage;

import com.dayan.platform.config.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.ComposeObjectArgs;
import io.minio.ComposeSource;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Repository;

@Repository
public class MinioObjectStorage implements ObjectStorage {

    private static final long MULTIPART_PART_SIZE = 10L * 1024 * 1024;

    private final MinioClient client;
    private final MinioClient presignClient;
    private final MinioProperties properties;

    public MinioObjectStorage(MinioClient client, MinioProperties properties) {
        this.client = client;
        this.presignClient = MinioClient.builder()
                .endpoint(properties.publicEndpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
        this.properties = properties;
    }

    @Override
    public void ensureBucket() {
        try {
            boolean exists = client.bucketExists(
                    BucketExistsArgs.builder().bucket(properties.bucket()).build()
            );
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(properties.bucket()).build());
            }
        } catch (Exception exception) {
            if (bucketExistsAfterConcurrentCreation()) {
                return;
            }
            throw new ObjectStorageException("Unable to initialize object storage bucket", exception);
        }
    }

    @Override
    public String put(String objectKey, InputStream inputStream, long size, String contentType) {
        try {
            return client.putObject(
                    PutObjectArgs.builder()
                            .bucket(properties.bucket())
                            .object(objectKey)
                            .stream(inputStream, size, MULTIPART_PART_SIZE)
                            .contentType(contentType)
                            .build()
            ).etag();
        } catch (Exception exception) {
            throw new ObjectStorageException("Unable to store object", exception);
        }
    }

    @Override
    public String compose(String objectKey, List<String> sourceObjectKeys, String contentType) {
        try {
            List<ComposeSource> sources = sourceObjectKeys.stream()
                    .map(source -> ComposeSource.builder()
                            .bucket(properties.bucket())
                            .object(source)
                            .build())
                    .toList();
            return client.composeObject(
                    ComposeObjectArgs.builder()
                            .bucket(properties.bucket())
                            .object(objectKey)
                            .sources(sources)
                            .headers(Map.of("Content-Type", contentType))
                            .build()
            ).etag();
        } catch (Exception exception) {
            throw new ObjectStorageException("Unable to compose object", exception);
        }
    }

    @Override
    public StoredObject get(String objectKey) {
        try {
            return new StoredObject(client.getObject(
                    GetObjectArgs.builder()
                            .bucket(properties.bucket())
                            .object(objectKey)
                            .build()
            ));
        } catch (Exception exception) {
            throw new ObjectStorageException("Unable to read object", exception);
        }
    }

    @Override
    public String presignGet(
            String objectKey,
            String responseContentDisposition,
            Duration ttl
    ) {
        try {
            return presignClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(properties.bucket())
                            .object(objectKey)
                            .expiry(Math.toIntExact(ttl.toSeconds()), TimeUnit.SECONDS)
                            .extraQueryParams(Map.of(
                                    "response-content-disposition",
                                    responseContentDisposition
                            ))
                            .build()
            );
        } catch (Exception exception) {
            throw new ObjectStorageException("Unable to create preview URL", exception);
        }
    }

    @Override
    public void remove(String objectKey) {
        try {
            client.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(properties.bucket())
                            .object(objectKey)
                            .build()
            );
        } catch (Exception exception) {
            throw new ObjectStorageException("Unable to remove object", exception);
        }
    }

    private boolean bucketExistsAfterConcurrentCreation() {
        try {
            return client.bucketExists(
                    BucketExistsArgs.builder().bucket(properties.bucket()).build()
            );
        } catch (Exception ignored) {
            return false;
        }
    }
}

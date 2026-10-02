package com.dayan.platform.repository.storage;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;
import io.minio.BucketExistsArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.messages.Item;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
public class MultiCloudStorageProbe implements CloudStorageProbe {

    private static final String AZURE_BLOB = "AZURE_BLOB";

    @Override
    public Usage probe(Connection connection) {
        try {
            if (AZURE_BLOB.equals(connection.provider())) {
                return probeAzure(connection);
            }
            return probeS3Compatible(connection);
        } catch (ObjectStorageException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ObjectStorageException("Unable to inspect cloud storage", exception);
        }
    }

    private Usage probeS3Compatible(Connection connection) throws Exception {
        MinioClient.Builder builder = MinioClient.builder()
                .endpoint(connection.endpoint())
                .credentials(connection.accessKey(), connection.secretKey());
        if (StringUtils.hasText(connection.region())) {
            builder.region(connection.region());
        }
        MinioClient client = builder.build();
        boolean exists = client.bucketExists(
                BucketExistsArgs.builder().bucket(connection.bucket()).build()
        );
        if (!exists) {
            throw new ObjectStorageException("Storage bucket does not exist", null);
        }

        long bytes = 0;
        long objects = 0;
        Iterable<Result<Item>> results = client.listObjects(
                ListObjectsArgs.builder()
                        .bucket(connection.bucket())
                        .recursive(true)
                        .build()
        );
        for (Result<Item> result : results) {
            Item item = result.get();
            if (!item.isDir()) {
                bytes = Math.addExact(bytes, item.size());
                objects++;
            }
        }
        return new Usage(bytes, objects);
    }

    private Usage probeAzure(Connection connection) {
        StorageSharedKeyCredential credential = new StorageSharedKeyCredential(
                connection.accessKey(),
                connection.secretKey()
        );
        BlobContainerClient container = new BlobServiceClientBuilder()
                .endpoint(connection.endpoint())
                .credential(credential)
                .buildClient()
                .getBlobContainerClient(connection.bucket());
        if (!container.exists()) {
            throw new ObjectStorageException("Storage container does not exist", null);
        }

        long bytes = 0;
        long objects = 0;
        for (var blob : container.listBlobs()) {
            Long length = blob.getProperties().getContentLength();
            bytes = Math.addExact(bytes, length == null ? 0 : length);
            objects++;
        }
        return new Usage(bytes, objects);
    }
}

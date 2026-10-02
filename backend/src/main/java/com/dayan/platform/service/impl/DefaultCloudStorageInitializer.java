package com.dayan.platform.service.impl;

import com.dayan.platform.config.MinioProperties;
import com.dayan.platform.model.CloudStorage;
import com.dayan.platform.repository.mapper.CloudStorageMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class DefaultCloudStorageInitializer implements ApplicationRunner {

    private final CloudStorageMapper storageMapper;
    private final StorageCredentialCipher credentialCipher;
    private final MinioProperties properties;

    public DefaultCloudStorageInitializer(
            CloudStorageMapper storageMapper,
            StorageCredentialCipher credentialCipher,
            MinioProperties properties
    ) {
        this.storageMapper = storageMapper;
        this.credentialCipher = credentialCipher;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!storageMapper.selectAll().isEmpty()) {
            return;
        }
        CloudStorage storage = new CloudStorage();
        storage.setStorageKey("minio-default");
        storage.setName("MinIO Default Storage");
        storage.setProvider("MINIO");
        storage.setEndpoint(properties.endpoint());
        storage.setBucket(properties.bucket());
        storage.setAccessKeyCiphertext(credentialCipher.encrypt(properties.accessKey()));
        storage.setSecretKeyCiphertext(credentialCipher.encrypt(properties.secretKey()));
        storage.setDefaultStorage(true);
        storage.setEnabled(true);
        storage.setStatus("NEVER");
        storage.setUsageBytes(0L);
        storage.setObjectCount(0L);
        storageMapper.insert(storage);
    }
}

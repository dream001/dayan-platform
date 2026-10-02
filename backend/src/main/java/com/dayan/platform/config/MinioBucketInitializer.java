package com.dayan.platform.config;

import com.dayan.platform.repository.storage.ObjectStorage;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MinioBucketInitializer implements ApplicationRunner {

    private final ObjectStorage objectStorage;

    public MinioBucketInitializer(ObjectStorage objectStorage) {
        this.objectStorage = objectStorage;
    }

    @Override
    public void run(ApplicationArguments args) {
        objectStorage.ensureBucket();
    }
}

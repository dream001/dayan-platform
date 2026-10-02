package com.dayan.platform.repository.config;

import com.dayan.platform.config.ApplicationProperties;
import com.dayan.platform.model.ApplicationMetadata;
import com.dayan.platform.repository.ApplicationMetadataRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

@Repository
public class EnvironmentApplicationMetadataRepository implements ApplicationMetadataRepository {

    private final String applicationName;
    private final ApplicationProperties properties;

    public EnvironmentApplicationMetadataRepository(
            @Value("${spring.application.name}") String applicationName,
            ApplicationProperties properties
    ) {
        this.applicationName = applicationName;
        this.properties = properties;
    }

    @Override
    public ApplicationMetadata get() {
        return new ApplicationMetadata(
                applicationName,
                properties.environment(),
                properties.version()
        );
    }
}

package com.dayan.platform.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

@SuppressWarnings("resource")
public abstract class PostgreSqlIntegrationTestSupport {

    protected static final String INITIAL_ADMIN_PASSWORD = "Integration-Admin-Password-2026";
    protected static final String MINIO_ACCESS_KEY = "integration-minio";
    protected static final String MINIO_SECRET_KEY = "integration-minio-password";
    protected static final String MINIO_BUCKET = "dayan-integration";
    protected static final PostgreSQLContainer<?> POSTGRESQL;
    protected static final GenericContainer<?> MINIO;

    static {
        POSTGRESQL = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("dayan_test")
                .withUsername("dayan_test")
                .withPassword("dayan_test");
        MINIO = new GenericContainer<>(
                DockerImageName.parse("minio/minio:RELEASE.2023-03-20T20-16-18Z")
        )
                .withEnv("MINIO_ROOT_USER", MINIO_ACCESS_KEY)
                .withEnv("MINIO_ROOT_PASSWORD", MINIO_SECRET_KEY)
                .withCommand("server", "/data")
                .withExposedPorts(9000)
                .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));
        POSTGRESQL.start();
        MINIO.start();
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
        registry.add("app.initial-admin.enabled", () -> true);
        registry.add("app.initial-admin.username", () -> "integration-admin");
        registry.add("app.initial-admin.display-name", () -> "Integration Administrator");
        registry.add("app.initial-admin.email", () -> "integration-admin@example.com");
        registry.add("app.initial-admin.password", () -> INITIAL_ADMIN_PASSWORD);
        registry.add(
                "app.storage.endpoint",
                () -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000)
        );
        registry.add(
                "app.storage.public-endpoint",
                () -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000)
        );
        registry.add("app.storage.access-key", () -> MINIO_ACCESS_KEY);
        registry.add("app.storage.secret-key", () -> MINIO_SECRET_KEY);
        registry.add("app.storage.bucket", () -> MINIO_BUCKET);
        registry.add("app.storage.max-file-size", () -> "1KB");
        registry.add("spring.servlet.multipart.max-file-size", () -> "1KB");
        registry.add("spring.servlet.multipart.max-request-size", () -> "4KB");
    }
}

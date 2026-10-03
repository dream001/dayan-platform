package com.dayan.platform.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dayan.platform.support.PostgreSqlIntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.StatObjectArgs;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class DataUploadIntegrationTest extends PostgreSqlIntegrationTestSupport {

    private static final String BASE = "/api/v1/data/uploads";
    private static final int CHUNK_SIZE = 10 * 1024 * 1024;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private io.minio.MinioClient minioClient;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private long projectId;

    @DynamicPropertySource
    static void uploadLimits(DynamicPropertyRegistry registry) {
        registry.add("spring.servlet.multipart.max-file-size", () -> "20MB");
        registry.add("spring.servlet.multipart.max-request-size", () -> "21MB");
    }

    @BeforeEach
    void prepare() {
        jdbcTemplate.update("DELETE FROM data_upload_part");
        jdbcTemplate.update("DELETE FROM data_upload_session");
        jdbcTemplate.update("DELETE FROM data_dataset");
        jdbcTemplate.update("DELETE FROM file_metadata");
        jdbcTemplate.update("DELETE FROM basic_project_member");
        jdbcTemplate.update("DELETE FROM basic_project");
        jdbcTemplate.update(
                "UPDATE sys_user SET password_hash = ?, enabled = TRUE WHERE username = 'integration-admin'",
                passwordEncoder.encode(INITIAL_ADMIN_PASSWORD)
        );
        Long adminId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE username = 'integration-admin'",
                Long.class
        );
        projectId = jdbcTemplate.queryForObject("""
                INSERT INTO basic_project
                    (code, name, project_type, access_level, status, storage_provider,
                     storage_quota_bytes, quality_threshold, review_mode,
                     notification_enabled, owner_id)
                VALUES ('UPLOAD_TEST', '上传测试项目', 'TEAM', 'PRIVATE', 'ACTIVE', 'MINIO',
                        1073741824, 90, 'SINGLE_REVIEW', TRUE, ?)
                RETURNING id
                """, Long.class, adminId);
    }

    @Test
    void uploadsDirectFileAndReusesDuplicateDataset() throws Exception {
        String token = login();
        mockMvc.perform(get(BASE + "/options").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projects[0].id").value(projectId))
                .andExpect(jsonPath("$.data.storages[0].key").value("minio-default"))
                .andExpect(jsonPath("$.data.multipartThreshold").value(104857600))
                .andExpect(jsonPath("$.data.chunkSize").value(CHUNK_SIZE));

        MockMultipartFile file = new MockMultipartFile(
                "file", "capture.mcap", MediaType.APPLICATION_OCTET_STREAM_VALUE,
                "mcap-content".getBytes(StandardCharsets.UTF_8)
        );
        MvcResult first = mockMvc.perform(multipart(BASE + "/direct")
                        .file(file)
                        .param("projectId", String.valueOf(projectId))
                        .param("storageKey", "minio-default")
                        .param("dataType", "MCAP")
                        .param("sourceFingerprint", "same-source")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("capture"))
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andReturn();
        long datasetId = data(first).path("id").asLong();

        mockMvc.perform(multipart(BASE + "/direct")
                        .file(file)
                        .param("projectId", String.valueOf(projectId))
                        .param("storageKey", "minio-default")
                        .param("dataType", "MCAP")
                        .param("sourceFingerprint", "same-source")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(datasetId));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM data_dataset", Long.class)).isOne();

        Long secondProjectId = jdbcTemplate.queryForObject("""
                INSERT INTO basic_project
                    (code, name, project_type, access_level, status, storage_provider,
                     storage_quota_bytes, quality_threshold, review_mode,
                     notification_enabled, owner_id)
                SELECT 'UPLOAD_TEST_2', '上传测试项目二', 'TEAM', 'PRIVATE', 'ACTIVE', 'MINIO',
                       1073741824, 90, 'SINGLE_REVIEW', TRUE, id
                FROM sys_user
                WHERE username = 'integration-admin'
                RETURNING id
                """, Long.class);
        MvcResult crossProject = mockMvc.perform(multipart(BASE + "/direct")
                        .file(file)
                        .param("projectId", String.valueOf(secondProjectId))
                        .param("storageKey", "minio-default")
                        .param("dataType", "MCAP")
                        .param("sourceFingerprint", "same-source")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectId").value(secondProjectId))
                .andReturn();
        assertThat(data(crossProject).path("id").asLong()).isNotEqualTo(datasetId);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM data_dataset", Long.class)).isEqualTo(2);
    }

    @ParameterizedTest(name = "{0} accepts {1}")
    @MethodSource("uploadFormats")
    void uploadsEverySupportedDatasetType(
            String dataType,
            String fileName,
            String contentType,
            String expectedStatus,
            String robotType
    ) throws Exception {
        String token = login();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                fileName,
                contentType,
                ("sample-" + dataType).getBytes(StandardCharsets.UTF_8)
        );
        var request = multipart(BASE + "/direct")
                .file(file)
                .param("projectId", String.valueOf(projectId))
                .param("storageKey", "minio-default")
                .param("dataType", dataType)
                .param("sourceFingerprint", "format-" + dataType.toLowerCase())
                .header("Authorization", bearer(token));
        if (robotType != null) {
            request.param("robotType", robotType);
        }
        if ("VIDEO".equals(dataType)) {
            request.param("durationSeconds", "12.345");
        }

        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dataType").value(dataType))
                .andExpect(jsonPath("$.data.originalName").value(fileName))
                .andExpect(jsonPath("$.data.contentType").value(contentType))
                .andExpect(jsonPath("$.data.status").value(expectedStatus))
                .andReturn();
        long datasetId = data(result).path("id").asLong();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM data_dataset WHERE id = ? AND data_type = ?",
                Long.class,
                datasetId,
                dataType
        )).isOne();
        if ("VIDEO".equals(dataType)) {
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT duration_seconds FROM data_dataset WHERE id = ?",
                    java.math.BigDecimal.class,
                    datasetId
            )).isEqualByComparingTo("12.345");
        }
    }

    @Test
    void pausesResumesAndCompletesMultipartUpload() throws Exception {
        String token = login();
        long totalSize = CHUNK_SIZE + 3L;
        String request = """
                {
                  "projectId": %d,
                  "storageKey": "minio-default",
                  "dataType": "MCAP",
                  "fileName": "large.mcap",
                  "contentType": "application/octet-stream",
                  "totalSize": %d,
                  "sourceFingerprint": "multipart-source"
                }
                """.formatted(projectId, totalSize);
        JsonNode session = data(mockMvc.perform(post(BASE + "/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalChunks").value(2))
                .andReturn());
        String sessionId = session.path("id").asText();

        uploadPart(token, sessionId, 0, new byte[CHUNK_SIZE]);
        mockMvc.perform(post(BASE + "/sessions/{id}/pause", sessionId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(post(BASE + "/sessions/{id}/resume", sessionId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uploadedParts[0]").value(0));
        uploadPart(token, sessionId, 1, new byte[]{1, 2, 3});

        JsonNode dataset = data(mockMvc.perform(post(BASE + "/sessions/{id}/complete", sessionId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sizeBytes").value(totalSize))
                .andReturn());
        String objectKey = jdbcTemplate.queryForObject("""
                SELECT f.object_key
                FROM data_dataset d JOIN file_metadata f ON f.id = d.file_id
                WHERE d.id = ?
                """, String.class, dataset.path("id").asLong());
        assertThat(minioClient.statObject(
                StatObjectArgs.builder().bucket(MINIO_BUCKET).object(objectKey).build()
        ).size()).isEqualTo(totalSize);
    }

    private void uploadPart(String token, String sessionId, int part, byte[] bytes) throws Exception {
        mockMvc.perform(multipart(BASE + "/sessions/{id}/parts/{part}", sessionId, part)
                        .file(new MockMultipartFile(
                                "chunk", "part-" + part, MediaType.APPLICATION_OCTET_STREAM_VALUE, bytes
                        ))
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    private String login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"integration-admin","password":"%s"}
                                """.formatted(INITIAL_ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return data(result).path("accessToken").asText();
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).path("data");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private static Stream<Arguments> uploadFormats() {
        return Stream.of(
                Arguments.of("MCAP", "sample.mcap", "application/octet-stream", "READY", null),
                Arguments.of("BAG", "sample.bag", "application/octet-stream", "PROCESSING", null),
                Arguments.of("VIDEO", "sample.mp4", "video/mp4", "PROCESSING", null),
                Arguments.of("AUDIO", "sample.mp3", "audio/mpeg", "PROCESSING", null),
                Arguments.of("IMAGE", "sample.png", "image/png", "READY", null),
                Arguments.of("HDF5", "sample.h5", "application/x-hdf5", "PROCESSING", "generic"),
                Arguments.of("LEROBOT", "lerobot.tar", "application/x-tar", "PROCESSING", null),
                Arguments.of("MEITUAN", "meituan.tar", "application/x-tar", "PROCESSING", null),
                Arguments.of("LUMOS", "sample.lumos", "application/octet-stream", "PROCESSING", null),
                Arguments.of("ZC0TOUCH", "sample.zc0touch", "application/octet-stream", "PROCESSING", null),
                Arguments.of("SENSEXPERIENCE", "sense.tar", "application/x-tar", "PROCESSING", null),
                Arguments.of("BVH", "sample.bvh", "text/plain", "READY", null)
        );
    }
}

package com.dayan.platform.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dayan.platform.support.PostgreSqlIntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class FileManagementIntegrationTest extends PostgreSqlIntegrationTestSupport {

    private static final String FILES_PATH = "/api/v1/files";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private MinioClient minioClient;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanFiles() throws Exception {
        jdbcTemplate.update("DELETE FROM file_metadata");
        jdbcTemplate.update("DELETE FROM auth_session");
        jdbcTemplate.update(
                "UPDATE sys_user SET password_hash = ?, enabled = TRUE WHERE username = 'integration-admin'",
                passwordEncoder.encode(INITIAL_ADMIN_PASSWORD)
        );
        removeAllObjects();
    }

    @AfterEach
    void removeFailureTrigger() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS reject_test_file ON file_metadata");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS reject_test_file_metadata()");
    }

    @Test
    void uploadsPagesReadsPreviewsDownloadsAndDeletesRealObject() throws Exception {
        String token = login();
        MockMultipartFile upload = new MockMultipartFile(
                "file",
                "../../evil\r\nname.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "stored in minio".getBytes(StandardCharsets.UTF_8)
        );

        JsonNode created = data(mockMvc.perform(multipart(FILES_PATH)
                        .file(upload)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.originalName").value("evilname.txt"))
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andReturn());
        long id = created.path("id").asLong();
        String objectKey = jdbcTemplate.queryForObject(
                "SELECT object_key FROM file_metadata WHERE id = ?",
                String.class,
                id
        );
        assertThat(objectKey).matches("\\d{4}/\\d{2}/\\d{2}/[0-9a-f-]{36}");
        assertThat(minioClient.statObject(
                StatObjectArgs.builder().bucket(MINIO_BUCKET).object(objectKey).build()
        ).size()).isEqualTo(upload.getSize());

        mockMvc.perform(get(FILES_PATH)
                        .queryParam("page", "1")
                        .queryParam("size", "10")
                        .queryParam("keyword", "evil")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(id));

        mockMvc.perform(get(FILES_PATH + "/{id}", id)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.originalName").value("evilname.txt"));

        mockMvc.perform(get(FILES_PATH + "/{id}/download", id)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(content().bytes(upload.getBytes()));

        JsonNode preview = data(mockMvc.perform(get(FILES_PATH + "/{id}/preview", id)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url").isNotEmpty())
                .andReturn());
        HttpResponse<byte[]> previewResponse = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(preview.path("url").asText())).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray()
        );
        assertThat(previewResponse.statusCode()).isEqualTo(200);
        assertThat(previewResponse.body()).isEqualTo(upload.getBytes());

        mockMvc.perform(delete(FILES_PATH + "/{id}", id)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM file_metadata WHERE id = ?",
                Long.class,
                id
        )).isZero();
        assertThatThrownBy(() -> minioClient.statObject(
                StatObjectArgs.builder().bucket(MINIO_BUCKET).object(objectKey).build()
        )).isInstanceOf(Exception.class);
    }

    @Test
    void rejectsDisallowedTypeAndOversizedFileWithoutPersistingData() throws Exception {
        String token = login();
        MockMultipartFile invalidType = new MockMultipartFile(
                "file",
                "payload.exe",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                new byte[]{1}
        );
        mockMvc.perform(multipart(FILES_PATH)
                        .file(invalidType)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("FILE_TYPE_NOT_ALLOWED"));

        MockMultipartFile oversized = new MockMultipartFile(
                "file",
                "large.txt",
                MediaType.TEXT_PLAIN_VALUE,
                new byte[1025]
        );
        mockMvc.perform(multipart(FILES_PATH)
                        .file(oversized)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM file_metadata", Long.class)).isZero();
        assertThat(objectCount()).isZero();
    }

    @Test
    void removesObjectWhenPostgreSqlMetadataInsertFails() throws Exception {
        jdbcTemplate.execute("""
                CREATE FUNCTION reject_test_file_metadata() RETURNS trigger AS $$
                BEGIN
                  IF NEW.original_name = 'force-db-failure.txt' THEN
                    RAISE EXCEPTION 'injected metadata failure';
                  END IF;
                  RETURN NEW;
                END;
                $$ LANGUAGE plpgsql
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER reject_test_file
                BEFORE INSERT ON file_metadata
                FOR EACH ROW EXECUTE FUNCTION reject_test_file_metadata()
                """);
        String token = login();

        mockMvc.perform(multipart(FILES_PATH)
                        .file(new MockMultipartFile(
                                "file",
                                "force-db-failure.txt",
                                MediaType.TEXT_PLAIN_VALUE,
                                "must be compensated".getBytes(StandardCharsets.UTF_8)
                        ))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("COMMON_INTERNAL_ERROR"));

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM file_metadata", Long.class)).isZero();
        assertThat(objectCount()).isZero();
    }

    @Test
    void rejectsAuthenticatedUserWithoutUploadPermission() throws Exception {
        String token = login();
        jdbcTemplate.update("""
                DELETE FROM sys_role_permission
                WHERE role_id = 1
                  AND permission_id = (
                    SELECT id FROM sys_menu_permission WHERE code = 'file:upload'
                  )
                """);
        try {
            mockMvc.perform(multipart(FILES_PATH)
                            .file(new MockMultipartFile(
                                    "file",
                                    "forbidden.txt",
                                    MediaType.TEXT_PLAIN_VALUE,
                                    "forbidden".getBytes(StandardCharsets.UTF_8)
                            ))
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
            assertThat(objectCount()).isZero();
        } finally {
            jdbcTemplate.update("""
                    INSERT INTO sys_role_permission (role_id, permission_id)
                    SELECT 1, id FROM sys_menu_permission WHERE code = 'file:upload'
                    ON CONFLICT DO NOTHING
                    """);
        }
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

    private long objectCount() {
        long count = 0;
        for (var ignored : minioClient.listObjects(
                ListObjectsArgs.builder().bucket(MINIO_BUCKET).recursive(true).build()
        )) {
            count++;
        }
        return count;
    }

    private void removeAllObjects() throws Exception {
        for (var result : minioClient.listObjects(
                ListObjectsArgs.builder().bucket(MINIO_BUCKET).recursive(true).build()
        )) {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(MINIO_BUCKET)
                            .object(result.get().objectName())
                            .build()
            );
        }
    }
}

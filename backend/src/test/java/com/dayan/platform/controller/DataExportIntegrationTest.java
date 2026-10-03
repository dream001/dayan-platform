package com.dayan.platform.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dayan.platform.service.impl.DataExportTaskWorker;
import com.dayan.platform.support.PostgreSqlIntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "app.environment=test",
        "app.version=test",
        "app.export.poll-interval=3600000"
})
@AutoConfigureMockMvc
@Transactional
class DataExportIntegrationTest extends PostgreSqlIntegrationTestSupport {

    private static final String EXPORTS = "/api/v1/data/exports";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private DataExportTaskWorker taskWorker;

    private long adminId;
    private String adminToken;

    @BeforeEach
    void loginAdministrator() throws Exception {
        jdbcTemplate.update("DELETE FROM auth_session");
        jdbcTemplate.update(
                """
                UPDATE sys_user
                SET password_hash = ?, enabled = TRUE, updated_at = CURRENT_TIMESTAMP
                WHERE username = 'integration-admin'
                """,
                passwordEncoder.encode(INITIAL_ADMIN_PASSWORD)
        );
        adminId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE username = 'integration-admin'",
                Long.class
        );
        adminToken = login("integration-admin", INITIAL_ADMIN_PASSWORD);
    }

    @Test
    void exportsCompletedDatasetAsJsonAndEnforcesMonthlyQuota() throws Exception {
        long projectId = insertProject();
        long completedDataset = insertDataset(projectId, "completed-export", "COMPLETED");
        insertDataset(projectId, "unfinished-export", "ASSIGNED");
        jdbcTemplate.update(
                """
                INSERT INTO data_annotation
                    (dataset_id, annotator_id, content_text, is_qualified, reviewed)
                VALUES (?, ?, 'pick object', TRUE, TRUE)
                """,
                completedDataset,
                adminId
        );
        jdbcTemplate.update(
                """
                INSERT INTO data_export_quota (user_id, quota_limit, updated_by)
                VALUES (?, 1, ?)
                ON CONFLICT (user_id) DO UPDATE SET quota_limit = 1, updated_by = EXCLUDED.updated_by
                """,
                adminId,
                adminId
        );

        mockMvc.perform(get(EXPORTS + "/datasets")
                        .header("Authorization", bearer(adminToken))
                        .queryParam("projectId", String.valueOf(projectId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(completedDataset));

        JsonNode created = responseData(mockMvc.perform(post(EXPORTS)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "integration-json",
                                "format", "JSON",
                                "datasetIds", new long[]{completedDataset}
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn());
        long taskId = created.path("id").asLong();

        taskWorker.dispatch();

        mockMvc.perform(get(EXPORTS + "/" + taskId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.task.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.task.progress").value(100))
                .andExpect(jsonPath("$.data.datasets.length()").value(1));

        MvcResult download = mockMvc.perform(get(EXPORTS + "/" + taskId + "/download")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn();
        String content = download.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(content).contains("\"format\" : \"JSON\"");
        assertThat(content).contains("pick object");

        mockMvc.perform(post(EXPORTS)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "over-quota",
                                "format", "CSV",
                                "datasetIds", new long[]{completedDataset}
                        ))))
                .andExpect(status().isConflict());
    }

    private long insertProject() {
        long projectId = jdbcTemplate.queryForObject(
                """
                INSERT INTO basic_project
                    (code, name, project_type, access_level, storage_provider,
                     storage_quota_bytes, owner_id)
                VALUES ('EXPORT_TEST', 'Export test', 'TEAM', 'PRIVATE',
                        'MINIO', 1073741824, ?)
                RETURNING id
                """,
                Long.class,
                adminId
        );
        jdbcTemplate.update(
                """
                INSERT INTO basic_project_member
                    (project_id, user_id, role, data_access_level, assigned_by)
                VALUES (?, ?, 'PROJECT_ADMIN', 'FULL', ?)
                """,
                projectId,
                adminId,
                adminId
        );
        return projectId;
    }

    private long insertDataset(long projectId, String name, String annotationStatus) {
        long fileId = jdbcTemplate.queryForObject(
                """
                INSERT INTO file_metadata
                    (bucket_name, object_key, original_name, content_type, size_bytes, status)
                VALUES (?, ?, ?, 'video/mp4', 1024, 'READY')
                RETURNING id
                """,
                Long.class,
                MINIO_BUCKET,
                "exports/" + name + ".mp4",
                name + ".mp4"
        );
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO data_dataset
                    (name, file_id, data_type, size_bytes, annotation_status,
                     project_id, uploader_id)
                VALUES (?, ?, 'VIDEO', 1024, ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                name,
                fileId,
                annotationStatus,
                projectId,
                adminId
        );
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        return responseData(result).path("accessToken").asText();
    }

    private JsonNode responseData(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).path("data");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}

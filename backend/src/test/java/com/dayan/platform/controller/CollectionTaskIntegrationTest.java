package com.dayan.platform.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dayan.platform.support.PostgreSqlIntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CollectionTaskIntegrationTest extends PostgreSqlIntegrationTestSupport {

    private static final String COLLECTIONS = "/api/v1/collections";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminToken;
    private long projectId;
    private long collectorId;

    @BeforeEach
    void prepareData() throws Exception {
        jdbcTemplate.update("DELETE FROM auth_session");
        jdbcTemplate.update(
                """
                UPDATE sys_user
                SET password_hash = ?, enabled = TRUE, updated_at = CURRENT_TIMESTAMP
                WHERE username = 'integration-admin'
                """,
                passwordEncoder.encode(INITIAL_ADMIN_PASSWORD)
        );
        long adminId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE username = 'integration-admin'",
                Long.class
        );
        collectorId = jdbcTemplate.queryForObject(
                """
                INSERT INTO sys_user (username, password_hash, display_name)
                VALUES ('collection-worker', ?, 'Collection Worker')
                RETURNING id
                """,
                Long.class,
                passwordEncoder.encode("Collection-Worker-Password-2026")
        );
        long collectorRoleId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_role WHERE code = 'COLLECTOR'",
                Long.class
        );
        jdbcTemplate.update(
                "INSERT INTO sys_user_role (user_id, role_id, assigned_by) VALUES (?, ?, ?)",
                collectorId,
                collectorRoleId,
                adminId
        );
        projectId = jdbcTemplate.queryForObject(
                """
                INSERT INTO basic_project (
                    code, name, project_type, access_level, status, storage_provider,
                    storage_quota_bytes, quality_threshold, review_mode,
                    notification_enabled, owner_id
                )
                VALUES (
                    'collection-integration', 'Collection Integration', 'TEAM', 'PRIVATE',
                    'ACTIVE', 'MINIO', 1048576, 90, 'SINGLE_REVIEW', TRUE, ?
                )
                RETURNING id
                """,
                Long.class,
                adminId
        );
        jdbcTemplate.update(
                """
                INSERT INTO basic_project_member
                    (project_id, user_id, role, data_access_level, assigned_by)
                VALUES (?, ?, 'OBSERVER', 'READ_WRITE', ?)
                """,
                projectId,
                collectorId,
                adminId
        );
        adminToken = login("integration-admin", INITIAL_ADMIN_PASSWORD)
                .path("accessToken")
                .asText();
    }

    @Test
    void managesCollectionTaskLifecycleWithPersistentFiltersAndCounts() throws Exception {
        long taskId = responseData(mockMvc.perform(post(COLLECTIONS)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskPayload("Arm pick collection")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.status").value("PENDING"))
                .andExpect(jsonPath("$.data.summary.targetCount").value(12))
                .andExpect(jsonPath("$.data.assignees[0].username").value("collection-worker"))
                .andExpect(jsonPath("$.data.steps[0].actionName").value("Pick {A} into {B}"))
                .andReturn()).path("summary").path("id").asLong();

        mockMvc.perform(get(COLLECTIONS)
                        .header("Authorization", bearer(adminToken))
                        .queryParam("keyword", "Arm pick")
                        .queryParam("collectorId", String.valueOf(collectorId))
                        .queryParam("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(taskId));

        mockMvc.perform(get(COLLECTIONS + "/status-counts")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.statuses.PENDING").value(1));

        changeStatus(taskId, "APPROVED", 409);
        changeStatus(taskId, "WORKING", 200);
        changeStatus(taskId, "REVIEW_PENDING", 200);
        changeStatus(taskId, "APPROVED", 200);
        changeStatus(taskId, "SUBMITTED", 200);

        long fileId = jdbcTemplate.queryForObject(
                """
                INSERT INTO file_metadata (
                    bucket_name, object_key, original_name, content_type,
                    size_bytes, uploader_id, status
                )
                VALUES ('test', 'collection/sample.mcap', 'sample.mcap',
                        'application/octet-stream', 1024, ?, 'READY')
                RETURNING id
                """,
                Long.class,
                collectorId
        );
        long datasetId = jdbcTemplate.queryForObject(
                """
                INSERT INTO data_dataset (
                    name, file_id, data_type, size_bytes, project_id,
                    collector_id, uploader_id, metadata_status
                )
                VALUES ('sample.mcap', ?, 'MCAP', 1024, ?, ?, ?, 'READY')
                RETURNING id
                """,
                Long.class,
                fileId,
                projectId,
                collectorId,
                collectorId
        );
        mockMvc.perform(post(COLLECTIONS + "/" + taskId + "/datasets/" + datasetId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get(COLLECTIONS + "/" + taskId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.collectedCount").value(1))
                .andExpect(jsonPath("$.data.datasets[0].name").value("sample.mcap"));

        mockMvc.perform(delete(COLLECTIONS + "/" + taskId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted_at IS NOT NULL FROM data_collection_task WHERE id = ?",
                Boolean.class,
                taskId
        )).isTrue();
    }

    @Test
    void rejectsDuplicateNamesAndCollectorsOutsideProject() throws Exception {
        mockMvc.perform(post(COLLECTIONS)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskPayload("Unique task")))
                .andExpect(status().isOk());

        mockMvc.perform(post(COLLECTIONS)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskPayload("Unique task")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMON_CONFLICT"));

        String invalidCollectorPayload = taskPayload("Invalid collector")
                .replace("[" + collectorId + "]", "[999999]");
        mockMvc.perform(post(COLLECTIONS)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidCollectorPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_ARGUMENT"));
    }

    private void changeStatus(long taskId, String taskStatus, int expectedStatus) throws Exception {
        mockMvc.perform(patch(COLLECTIONS + "/" + taskId + "/status")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("status", taskStatus)
                        )))
                .andExpect(status().is(expectedStatus));
    }

    private String taskPayload(String name) throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of(
                "name", name,
                "projectId", projectId,
                "assigneeIds", new long[]{collectorId},
                "targetCount", 12,
                "averageDurationSeconds", 60,
                "notes", "Collect twelve stable samples",
                "initialScene", "Object centered on the workbench",
                "remoteOperationEnabled", false,
                "steps", new Object[]{
                        java.util.Map.of(
                                "actionName", "Pick {A} into {B}",
                                "objectName", "sample",
                                "targetName", "tray",
                                "notes", "Keep the camera stable"
                        )
                }
        ));
    }

    private JsonNode login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "username", username,
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        return responseData(result);
    }

    private JsonNode responseData(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}

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
        "app.version=test"
})
@AutoConfigureMockMvc
@Transactional
class AnnotationTaskIntegrationTest extends PostgreSqlIntegrationTestSupport {

    private static final String TASKS = "/api/v1/data/annotation-tasks";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PasswordEncoder passwordEncoder;

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
    void splitsDatasetsRunsWorkflowAndReleasesAssignmentsOnDelete() throws Exception {
        long projectId = insertProject();
        long annotatorA = insertUser("annotation-a", "Annotator A");
        long annotatorB = insertUser("annotation-b", "Annotator B");
        long reviewer = insertUser("annotation-reviewer", "Reviewer");
        insertProjectMember(projectId, annotatorA, "ANNOTATOR");
        insertProjectMember(projectId, annotatorB, "ANNOTATOR");
        insertProjectMember(projectId, reviewer, "REVIEWER");
        long firstDataset = insertDataset(projectId, "dataset-one");
        long secondDataset = insertDataset(projectId, "dataset-two");
        long thirdDataset = insertDataset(projectId, "dataset-three");

        JsonNode created = responseData(mockMvc.perform(post(TASKS)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Inspection",
                                "projectId", projectId,
                                "annotatorIds", new long[]{annotatorA, annotatorB},
                                "reviewerIds", new long[]{reviewer},
                                "datasetIds", new long[]{firstDataset, secondDataset, thirdDataset},
                                "randomOrder", false
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].summary.name").value("Inspection_Annotator A"))
                .andExpect(jsonPath("$.data[0].summary.datasetCount").value(2))
                .andExpect(jsonPath("$.data[1].summary.datasetCount").value(1))
                .andReturn());

        long taskId = created.get(0).path("summary").path("id").asLong();
        long relationId = created.get(0).path("datasets").get(0).path("relationId").asLong();

        mockMvc.perform(patch(TASKS + "/" + taskId + "/batch-annotation")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mode":"QUICK","description":"Pick and place the marked object"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.datasets[0].annotationDescription")
                        .value("Pick and place the marked object"));

        mockMvc.perform(patch(TASKS + "/" + taskId + "/datasets/" + relationId + "/review")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"VALID\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.datasets[0].checkResult").value("VALID"));

        for (String status : new String[]{"WORKING", "REVIEW_PENDING", "APPROVED", "SUBMITTED"}) {
            mockMvc.perform(patch(TASKS + "/" + taskId + "/status")
                            .header("Authorization", bearer(adminToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("status", status))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.summary.status").value(status));
        }

        mockMvc.perform(get(TASKS)
                        .header("Authorization", bearer(adminToken))
                        .queryParam("status", "SUBMITTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));

        mockMvc.perform(delete(TASKS + "/" + taskId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted_at IS NOT NULL FROM data_annotation_task WHERE id = ?",
                Boolean.class,
                taskId
        )).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM data_dataset WHERE task_id = ?",
                Integer.class,
                taskId
        )).isZero();
    }

    private long insertProject() {
        long projectId = jdbcTemplate.queryForObject(
                """
                INSERT INTO basic_project
                    (code, name, project_type, access_level, storage_provider,
                     storage_quota_bytes, owner_id)
                VALUES ('ANNOTATION_TEST', 'Annotation test', 'TEAM', 'PRIVATE',
                        'MINIO', 1073741824, ?)
                RETURNING id
                """,
                Long.class,
                adminId
        );
        insertProjectMember(projectId, adminId, "PROJECT_ADMIN");
        return projectId;
    }

    private long insertUser(String username, String displayName) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO sys_user (username, password_hash, display_name)
                VALUES (?, ?, ?)
                RETURNING id
                """,
                Long.class,
                username,
                passwordEncoder.encode("Annotation-Test-Password-2026"),
                displayName
        );
    }

    private void insertProjectMember(long projectId, long userId, String role) {
        jdbcTemplate.update(
                """
                INSERT INTO basic_project_member
                    (project_id, user_id, role, data_access_level, assigned_by)
                VALUES (?, ?, ?, 'READ_WRITE', ?)
                """,
                projectId,
                userId,
                role,
                adminId
        );
    }

    private long insertDataset(long projectId, String name) {
        long fileId = jdbcTemplate.queryForObject(
                """
                INSERT INTO file_metadata
                    (bucket_name, object_key, original_name, content_type, size_bytes, status)
                VALUES ('dayan-integration', ?, ?, 'video/mp4', 1024, 'READY')
                RETURNING id
                """,
                Long.class,
                "annotation/" + name + ".mp4",
                name + ".mp4"
        );
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO data_dataset
                    (name, file_id, data_type, size_bytes, project_id, uploader_id)
                VALUES (?, ?, 'VIDEO', 1024, ?, ?)
                RETURNING id
                """,
                Long.class,
                name,
                fileId,
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

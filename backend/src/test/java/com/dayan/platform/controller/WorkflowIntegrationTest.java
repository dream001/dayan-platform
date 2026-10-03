package com.dayan.platform.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WorkflowIntegrationTest extends PostgreSqlIntegrationTestSupport {

    private static final String WORKFLOWS = "/api/v1/workflows";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminToken;
    private long adminId;
    private long datasetId;

    @BeforeEach
    void setUp() throws Exception {
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
        long projectId = jdbcTemplate.queryForObject(
                """
                INSERT INTO basic_project
                    (code, name, project_type, access_level, storage_quota_bytes, owner_id)
                VALUES
                    ('workflow-it', 'Workflow Integration', 'TEAM', 'PRIVATE',
                     1073741824, ?)
                RETURNING id
                """,
                Long.class,
                adminId
        );
        long fileId = jdbcTemplate.queryForObject(
                """
                INSERT INTO file_metadata
                    (bucket_name, object_key, original_name, content_type,
                     size_bytes, uploader_id)
                VALUES
                    ('dayan-files', 'workflow/sample.mcap', 'sample.mcap',
                     'application/octet-stream', 10485760, ?)
                RETURNING id
                """,
                Long.class,
                adminId
        );
        datasetId = jdbcTemplate.queryForObject(
                """
                INSERT INTO data_dataset
                    (name, file_id, data_type, size_bytes, project_id, uploader_id)
                VALUES
                    ('workflow-sample', ?, 'MCAP', 10485760, ?, ?)
                RETURNING id
                """,
                Long.class,
                fileId,
                projectId,
                adminId
        );
        adminToken = login();
    }

    @Test
    void createsTestsAndRunsWorkflowWithRealDataset() throws Exception {
        long matchRuleId = responseData(mockMvc.perform(post(WORKFLOWS + "/match-rules")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "MCAP input",
                                  "description": "Matches MCAP files",
                                  "scope": "GLOBAL",
                                  "projectId": null,
                                  "priority": 10,
                                  "enabled": true,
                                  "logicOperator": "AND",
                                  "conditions": [{
                                    "field": "extension_name",
                                    "operator": "eq",
                                    "value": "mcap",
                                    "flags": ""
                                  }]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conditions[0].field").value("extension_name"))
                .andReturn()).path("id").asLong();

        long actionRuleId = responseData(mockMvc.perform(post(WORKFLOWS + "/action-rules")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Quality and export",
                                  "description": "Runs QC then exports",
                                  "scope": "GLOBAL",
                                  "projectId": null,
                                  "enabled": true,
                                  "steps": [
                                    {"action": "qualityCheck", "params": {"rubric": "HIGH"}},
                                    {"action": "exportDataset", "params": {"format": "mcap"}}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.steps.length()").value(2))
                .andReturn()).path("id").asLong();

        long workflowId = responseData(mockMvc.perform(post(WORKFLOWS + "/definitions")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "MCAP delivery",
                                "description", "Quality gates and delivery",
                                "scope", "GLOBAL",
                                "priority", 10,
                                "enabled", true,
                                "matchRuleId", matchRuleId,
                                "actionRuleId", actionRuleId
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stepCount").value(2))
                .andReturn()).path("id").asLong();

        mockMvc.perform(post(WORKFLOWS + "/match-rules/" + matchRuleId + "/test")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId": null, "datasetIds": [%d]}
                                """.formatted(datasetId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scanned").value(1))
                .andExpect(jsonPath("$.data.matched").value(1))
                .andExpect(jsonPath("$.data.samples[0].name").value("workflow-sample"));

        long runId = responseData(mockMvc.perform(post(WORKFLOWS + "/runs")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"workflowId": %d, "datasetId": %d}
                                """.formatted(workflowId, datasetId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.steps.length()").value(2))
                .andReturn()).path("id").asLong();

        mockMvc.perform(get(WORKFLOWS + "/overview")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workflowCount").value(1))
                .andExpect(jsonPath("$.data.queuedRunCount").value(1));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM workflow_run WHERE id = ?",
                String.class,
                runId
        )).isEqualTo("QUEUED");
    }

    private String login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "integration-admin",
                                "password", INITIAL_ADMIN_PASSWORD
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        return responseData(result).path("accessToken").asText();
    }

    private JsonNode responseData(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).path("data");
    }

    private String bearer() {
        return "Bearer " + adminToken;
    }
}

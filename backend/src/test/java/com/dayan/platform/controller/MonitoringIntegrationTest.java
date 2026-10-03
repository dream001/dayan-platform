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

@SpringBootTest(properties = "app.monitor.initial-delay=1h")
@AutoConfigureMockMvc
class MonitoringIntegrationTest extends PostgreSqlIntegrationTestSupport {

    private static final String MONITOR = "/api/v1/monitor";

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
    void resetData() throws Exception {
        jdbcTemplate.update("DELETE FROM request_access_log");
        jdbcTemplate.update("DELETE FROM monitor_metric_sample");
        jdbcTemplate.update("DELETE FROM data_export_task_dataset");
        jdbcTemplate.update("DELETE FROM data_export_task");
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
        adminToken = login();
        jdbcTemplate.update(
                "UPDATE monitor_queue_state SET paused = FALSE, updated_by = NULL"
        );
    }

    @Test
    void collectsMetricsAndReturnsSystemStatus() throws Exception {
        mockMvc.perform(post(MONITOR + "/collect")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.databaseLatencyMs").isNumber())
                .andExpect(jsonPath("$.data.queueBacklog").value(0))
                .andExpect(jsonPath("$.data.redisLatencyMs").doesNotExist());

        mockMvc.perform(get(MONITOR + "/overview")
                        .header("Authorization", bearer(adminToken))
                        .queryParam("range", "1h"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.range").value("1h"))
                .andExpect(jsonPath("$.data.trend.length()").value(1))
                .andExpect(jsonPath("$.data.components.length()").value(6))
                .andExpect(jsonPath("$.data.components[0].key").value("DATABASE"))
                .andExpect(jsonPath("$.data.components[1].status").value("NOT_CONFIGURED"))
                .andExpect(jsonPath("$.data.components[2].key").value("MINIO"))
                .andExpect(jsonPath("$.data.components[2].status").value("HEALTHY"))
                .andExpect(jsonPath("$.data.components[3].key").value("QUEUE"))
                .andExpect(jsonPath("$.data.components[4].key").value("CPU"))
                .andExpect(jsonPath("$.data.components[5].key").value("MEMORY"));

        mockMvc.perform(get(MONITOR + "/system")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cpuCores").isNumber())
                .andExpect(jsonPath("$.data.services[0].name").value("PostgreSQL"))
                .andExpect(jsonPath("$.data.services[0].status").value("UP"))
                .andExpect(jsonPath("$.data.services[1].status").value("NOT_CONFIGURED"))
                .andExpect(jsonPath("$.data.services[2].name").value("MinIO"));
    }

    @Test
    void persistsAccessLogsAndControlsExportQueue() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", bearer(adminToken))
                        .header("X-Request-Id", "monitor-access-test"))
                .andExpect(status().isOk());

        mockMvc.perform(get(MONITOR + "/access-logs")
                        .header("Authorization", bearer(adminToken))
                        .queryParam("path", "/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].username").value("integration-admin"))
                .andExpect(jsonPath("$.data.items[0].requestId").value("monitor-access-test"));

        long failedId = insertExport("Failed export", "FAILED");
        long pendingId = insertExport("Pending export", "PENDING");

        mockMvc.perform(get(MONITOR + "/queue")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pending").value(1))
                .andExpect(jsonPath("$.data.failed").value(1))
                .andExpect(jsonPath("$.data.paused").value(false));

        mockMvc.perform(post(MONITOR + "/queue/pause")
                        .header("Authorization", bearer(adminToken))
                        .queryParam("paused", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paused").value(true));

        mockMvc.perform(post(MONITOR + "/queue/tasks/" + failedId + "/retry")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post(MONITOR + "/queue/tasks/" + pendingId + "/cancel")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM data_export_task WHERE id = ?",
                String.class,
                failedId
        )).isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM data_export_task WHERE id = ?",
                String.class,
                pendingId
        )).isEqualTo("CANCELED");
    }

    private long insertExport(String name, String status) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO data_export_task (
                  name, format, status, progress, processed_count,
                  dataset_count, config_json, creator_id
                ) VALUES (?, 'JSON', ?, 0, 0, 1, '{}', ?)
                RETURNING id
                """,
                Long.class,
                name,
                status,
                adminId
        );
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
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return response.path("data").path("accessToken").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}

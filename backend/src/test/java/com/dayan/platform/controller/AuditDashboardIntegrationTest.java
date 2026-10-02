package com.dayan.platform.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dayan.platform.support.PostgreSqlIntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

@SpringBootTest
@AutoConfigureMockMvc
class AuditDashboardIntegrationTest extends PostgreSqlIntegrationTestSupport {

    private static final String AUDIT = "/api/v1/audit/logs";
    private static final String SYSTEM = "/api/v1/system";

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
        jdbcTemplate.update("DELETE FROM operation_log");
        jdbcTemplate.update("DELETE FROM auth_session");
        jdbcTemplate.update("DELETE FROM file_metadata WHERE bucket_name = 'audit-test'");
        jdbcTemplate.update(
                "DELETE FROM sys_user_role WHERE user_id IN (SELECT id FROM sys_user WHERE username LIKE 'audit-%')"
        );
        jdbcTemplate.update("DELETE FROM sys_user WHERE username LIKE 'audit-%'");
        jdbcTemplate.update("DELETE FROM sys_role WHERE code = 'AUDIT_VIEW_ONLY'");
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
        adminToken = login("integration-admin", INITIAL_ADMIN_PASSWORD, "setup-login");
    }

    @Test
    void persistsSuccessfulAndFailedAuditsWithoutSensitiveRequestFields() throws Exception {
        String secretPassword = "Never-Record-This-Password-2026";
        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Request-Id", "failed-login")
                        .header("User-Agent", "audit-integration-agent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "integration-admin",
                                "password", secretPassword
                        ))))
                .andExpect(status().isUnauthorized());

        MvcResult created = mockMvc.perform(post(SYSTEM + "/users")
                        .header("Authorization", bearer(adminToken))
                        .header("X-Request-Id", "create-user-audit")
                        .header("User-Agent", "audit-integration-agent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "audit-created",
                                  "password": "%s",
                                  "displayName": "Audit Created",
                                  "email": "audit-created@example.com",
                                  "phone": "",
                                  "enabled": true,
                                  "roleIds": []
                                }
                                """.formatted(secretPassword)))
                .andExpect(status().isOk())
                .andReturn();
        long createdUserId = responseData(created).path("id").asLong();

        mockMvc.perform(delete(SYSTEM + "/roles/1")
                        .header("Authorization", bearer(adminToken))
                        .header("X-Request-Id", "failed-role-delete"))
                .andExpect(status().isConflict());

        Map<String, Object> successful = jdbcTemplate.queryForMap(
                "SELECT * FROM operation_log WHERE request_id = 'create-user-audit'"
        );
        assertThat(successful)
                .containsEntry("operator_id", adminId)
                .containsEntry("operator_name", "integration-admin")
                .containsEntry("module", "USER")
                .containsEntry("action", "CREATE")
                .containsEntry("target_type", "USER")
                .containsEntry("target_id", Long.toString(createdUserId))
                .containsEntry("result", "SUCCESS")
                .containsEntry("user_agent", "audit-integration-agent");
        assertThat((Long) successful.get("duration_ms")).isNotNegative();
        assertThat(successful.get("ip_address")).isNotNull();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT result FROM operation_log WHERE request_id = 'failed-role-delete'",
                String.class
        )).isEqualTo("FAILURE");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT result FROM operation_log WHERE request_id = 'failed-login'",
                String.class
        )).isEqualTo("FAILURE");

        String persistedText = jdbcTemplate.queryForObject(
                """
                SELECT string_agg(
                    coalesce(details, '') || ' ' || coalesce(error_summary, ''),
                    ' '
                )
                FROM operation_log
                """,
                String.class
        );
        assertThat(persistedText)
                .doesNotContain(secretPassword)
                .doesNotContain("refreshToken")
                .doesNotContain("password_hash");
    }

    @Test
    void filtersAuditPagesAndRequiresSeparateDetailPermission() throws Exception {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        long matchingLogId = jdbcTemplate.queryForObject(
                """
                INSERT INTO operation_log
                    (operator_id, operator_name, module, action, target_type, target_id,
                     result, request_id, occurred_at, duration_ms)
                VALUES (?, 'integration-admin', 'FILE', 'DELETE', 'FILE', '42',
                        'FAILURE', 'filter-match', ?, 12)
                RETURNING id
                """,
                Long.class,
                adminId,
                now.minusMinutes(5)
        );
        jdbcTemplate.update(
                """
                INSERT INTO operation_log
                    (operator_id, operator_name, module, action, result, occurred_at)
                VALUES (?, 'integration-admin', 'USER', 'UPDATE', 'SUCCESS', ?)
                """,
                adminId,
                now.minusDays(2)
        );

        mockMvc.perform(get(AUDIT)
                        .header("Authorization", bearer(adminToken))
                        .queryParam("userId", Long.toString(adminId))
                        .queryParam("module", "file")
                        .queryParam("result", "failure")
                        .queryParam("startTime", now.minusHours(1).toString())
                        .queryParam("endTime", now.plusHours(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(matchingLogId))
                .andExpect(jsonPath("$.data.items[0].requestId").value("filter-match"));

        long viewPermissionId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu_permission WHERE code = 'audit:log:view'",
                Long.class
        );
        long roleId = jdbcTemplate.queryForObject(
                """
                INSERT INTO sys_role (name, code)
                VALUES ('Audit list viewer', 'AUDIT_VIEW_ONLY')
                RETURNING id
                """,
                Long.class
        );
        jdbcTemplate.update(
                "INSERT INTO sys_role_permission (role_id, permission_id) VALUES (?, ?)",
                roleId,
                viewPermissionId
        );
        long viewerId = insertUser("audit-viewer", "Audit-Viewer-Password-2026");
        jdbcTemplate.update(
                "INSERT INTO sys_user_role (user_id, role_id, assigned_by) VALUES (?, ?, ?)",
                viewerId,
                roleId,
                adminId
        );
        String viewerToken = login(
                "audit-viewer",
                "Audit-Viewer-Password-2026",
                "viewer-login"
        );

        mockMvc.perform(get(AUDIT).header("Authorization", bearer(viewerToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get(AUDIT + "/" + matchingLogId)
                        .header("Authorization", bearer(viewerToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(AUDIT + "/" + matchingLogId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ipAddress").doesNotExist())
                .andExpect(jsonPath("$.data.targetId").value("42"));
    }

    @Test
    void returnsDashboardStatisticsFromCurrentPostgreSqlRows() throws Exception {
        jdbcTemplate.update(
                """
                INSERT INTO sys_user (username, password_hash, display_name, enabled)
                VALUES ('audit-dashboard-disabled', ?, 'Dashboard Disabled', FALSE)
                """,
                passwordEncoder.encode("Dashboard-Disabled-Password-2026")
        );
        jdbcTemplate.update(
                """
                INSERT INTO file_metadata
                    (bucket_name, object_key, original_name, content_type, size_bytes, status)
                VALUES
                    ('audit-test', 'ready-object', 'ready.txt', 'text/plain', 321, 'READY'),
                    ('audit-test', 'failed-object', 'failed.txt', 'text/plain', 999, 'FAILED')
                """
        );
        jdbcTemplate.update(
                """
                INSERT INTO operation_log
                    (operator_id, operator_name, module, action, result, occurred_at, duration_ms)
                VALUES (?, 'integration-admin', 'FILE', 'UPLOAD', 'SUCCESS', CURRENT_TIMESTAMP, 3)
                """,
                adminId
        );

        long totalUsers = jdbcTemplate.queryForObject("SELECT count(*) FROM sys_user", Long.class);
        long enabledUsers = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM sys_user WHERE enabled = TRUE",
                Long.class
        );
        long totalFiles = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM file_metadata WHERE status = 'READY'",
                Long.class
        );
        long totalFileSize = jdbcTemplate.queryForObject(
                "SELECT coalesce(sum(size_bytes), 0) FROM file_metadata WHERE status = 'READY'",
                Long.class
        );
        long recentOperations = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM operation_log WHERE occurred_at >= CURRENT_TIMESTAMP - INTERVAL '7 days'",
                Long.class
        );

        mockMvc.perform(get("/api/v1/dashboard/statistics")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalUsers").value(totalUsers))
                .andExpect(jsonPath("$.data.enabledUsers").value(enabledUsers))
                .andExpect(jsonPath("$.data.totalFiles").value(totalFiles))
                .andExpect(jsonPath("$.data.totalFileSizeBytes").value(totalFileSize))
                .andExpect(jsonPath("$.data.recentOperationCount").value(recentOperations))
                .andExpect(jsonPath("$.data.recentOperations[0].module").value("FILE"));
    }

    private long insertUser(String username, String password) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO sys_user (username, password_hash, display_name)
                VALUES (?, ?, ?)
                RETURNING id
                """,
                Long.class,
                username,
                passwordEncoder.encode(password),
                username
        );
    }

    private String login(String username, String password, String requestId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Request-Id", requestId)
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

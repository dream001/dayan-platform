package com.dayan.platform.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class ProjectManagementIntegrationTest extends PostgreSqlIntegrationTestSupport {

    private static final String PROJECTS = "/api/v1/basic/projects";

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
    void managesProjectLifecycleConfigurationAndMembers() throws Exception {
        long projectId = createProject("VISION_2026", "Vision labeling", "PRIVATE");

        mockMvc.perform(get(PROJECTS + "/overview")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.planning").value(1));

        mockMvc.perform(get(PROJECTS + "/" + projectId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.code").value("VISION_2026"))
                .andExpect(jsonPath("$.data.summary.memberCount").value(1))
                .andExpect(jsonPath("$.data.qualityThreshold").value(95.5))
                .andExpect(jsonPath("$.data.reviewMode").value("DOUBLE_REVIEW"))
                .andExpect(jsonPath("$.data.metrics.datasetCount").value(0))
                .andExpect(jsonPath("$.data.metrics.storageUsedBytes").value(0))
                .andExpect(jsonPath("$.data.metrics.taskCompletionRate").value(0))
                .andExpect(jsonPath("$.data.metrics.qualityRate").value(0))
                .andExpect(jsonPath("$.data.metrics.activeMemberCount").value(1));

        long memberId = createManagerUser("project-member");
        mockMvc.perform(put(PROJECTS + "/" + projectId + "/members")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": %d,
                                  "role": "ANNOTATOR",
                                  "dataAccessLevel": "READ_WRITE",
                                  "validFrom": null,
                                  "validUntil": null
                                }
                                """.formatted(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("project-member"))
                .andExpect(jsonPath("$.data.active").value(true));

        String memberToken = login("project-member", "Project-Member-Password-2026");
        mockMvc.perform(get(PROJECTS).header("Authorization", bearer(memberToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].currentRole").value("ANNOTATOR"))
                .andExpect(jsonPath("$.data.items[0].canEdit").value(false));

        mockMvc.perform(put(PROJECTS + "/" + projectId)
                        .header("Authorization", bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(projectJson("VISION_2026", "Forbidden update", "PRIVATE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));

        mockMvc.perform(put(PROJECTS + "/" + projectId + "/members")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": %d,
                                  "role": "PROJECT_MANAGER",
                                  "dataAccessLevel": "FULL",
                                  "validFrom": null,
                                  "validUntil": null
                                }
                                """.formatted(memberId)))
                .andExpect(status().isOk());

        mockMvc.perform(put(PROJECTS + "/" + projectId)
                        .header("Authorization", bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(projectJson("VISION_2026", "Updated vision project", "RESTRICTED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.name").value("Updated vision project"));

        mockMvc.perform(patch(PROJECTS + "/" + projectId + "/status")
                        .header("Authorization", bearer(memberToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.status").value("ACTIVE"));

        mockMvc.perform(delete(PROJECTS + "/" + projectId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM operation_log WHERE module = 'PROJECT'",
                Integer.class
        )).isGreaterThanOrEqualTo(5);
    }

    @Test
    void exposesPublicProjectsButProtectsPrivateProjectsAndOwnerMembership() throws Exception {
        long publicId = createProject("PUBLIC_SAMPLE", "Public sample", "PUBLIC");
        long privateId = createProject("PRIVATE_SAMPLE", "Private sample", "PRIVATE");
        long viewerId = createManagerUser("project-viewer");
        String viewerToken = login("project-viewer", "Project-Member-Password-2026");

        mockMvc.perform(get(PROJECTS).header("Authorization", bearer(viewerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(publicId));
        mockMvc.perform(get(PROJECTS + "/" + privateId)
                        .header("Authorization", bearer(viewerToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete(PROJECTS + "/" + publicId + "/members/" + adminId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict());
        assertThat(viewerId).isPositive();
    }

    private long createProject(String code, String name, String accessLevel) throws Exception {
        return responseData(mockMvc.perform(post(PROJECTS)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(projectJson(code, name, accessLevel)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.status").value("PLANNING"))
                .andReturn()).path("summary").path("id").asLong();
    }

    private String projectJson(String code, String name, String accessLevel) {
        return """
                {
                  "code": "%s",
                  "name": "%s",
                  "description": "Image annotation delivery project",
                  "projectType": "TEAM",
                  "accessLevel": "%s",
                  "storageProvider": "MINIO",
                  "storageQuotaBytes": 10737418240,
                  "startDate": "2026-10-02",
                  "endDate": "2026-12-31",
                  "annotationGuideline": "Use the approved labeling taxonomy.",
                  "qualityThreshold": 95.5,
                  "reviewMode": "DOUBLE_REVIEW",
                  "notificationEnabled": true
                }
                """.formatted(code, name, accessLevel);
    }

    private long createManagerUser(String username) {
        long userId = jdbcTemplate.queryForObject(
                """
                INSERT INTO sys_user (username, password_hash, display_name)
                VALUES (?, ?, ?)
                RETURNING id
                """,
                Long.class,
                username,
                passwordEncoder.encode("Project-Member-Password-2026"),
                "Project Member"
        );
        long managerRoleId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_role WHERE code = 'MANAGER'",
                Long.class
        );
        jdbcTemplate.update(
                "INSERT INTO sys_user_role (user_id, role_id, assigned_by) VALUES (?, ?, ?)",
                userId,
                managerRoleId,
                adminId
        );
        return userId;
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

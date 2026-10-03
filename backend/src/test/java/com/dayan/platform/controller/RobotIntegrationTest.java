package com.dayan.platform.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class RobotIntegrationTest extends PostgreSqlIntegrationTestSupport {

    private static final String ROBOTS = "/api/v1/basic/robots";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PasswordEncoder passwordEncoder;

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
        adminToken = login("integration-admin", INITIAL_ADMIN_PASSWORD);
    }

    @Test
    void exposesSeedCatalogAndManagesCustomRobotWithSoftDelete() throws Exception {
        mockMvc.perform(get(ROBOTS)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(23))
                .andExpect(jsonPath("$.data[0].datasetCount").isNumber());

        MvcResult created = mockMvc.perform(post(ROBOTS)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(robotJson("integration-arm", "集成机械臂")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("integration-arm"))
                .andExpect(jsonPath("$.data.builtIn").value(false))
                .andReturn();
        long id = responseData(created).path("id").asLong();

        mockMvc.perform(put(ROBOTS + "/" + id)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(robotJson("integration-arm", "更新后的机械臂")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.titleZh").value("更新后的机械臂"));

        mockMvc.perform(get(ROBOTS + "/" + id + "/datasets")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(delete(ROBOTS + "/" + id)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted FROM basic_robot WHERE id = ?",
                Boolean.class,
                id
        )).isTrue();

        mockMvc.perform(post(ROBOTS)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(robotJson("integration-arm", "重名机械臂")))
                .andExpect(status().isConflict());
    }

    @Test
    void allowsManagerToBrowseButNotMaintainRobots() throws Exception {
        long userId = jdbcTemplate.queryForObject(
                """
                INSERT INTO sys_user (username, password_hash, display_name)
                VALUES ('robot-manager', ?, 'Robot Manager')
                RETURNING id
                """,
                Long.class,
                passwordEncoder.encode("Robot-Manager-Password-2026")
        );
        jdbcTemplate.update(
                """
                INSERT INTO sys_user_role (user_id, role_id)
                SELECT ?, id FROM sys_role WHERE code = 'MANAGER'
                """,
                userId
        );
        String managerToken = login("robot-manager", "Robot-Manager-Password-2026");

        mockMvc.perform(get(ROBOTS + "?robotType=HUMANOID")
                        .header("Authorization", bearer(managerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(5));

        mockMvc.perform(post(ROBOTS)
                        .header("Authorization", bearer(managerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(robotJson("forbidden-arm", "无权新增")))
                .andExpect(status().isForbidden());
    }

    private String robotJson(String name, String titleZh) {
        return """
                {
                  "name": "%s",
                  "iconUrl": "https://example.com/robot.png",
                  "titleZh": "%s",
                  "titleEn": "Integration Arm",
                  "robotType": "DESKTOP_ARM",
                  "actionMappingSupport": "SUPPORTED",
                  "description": "Integration test robot",
                  "company": "Dayan",
                  "introductionUrl": "https://example.com/robots/integration-arm"
                }
                """.formatted(name, titleZh);
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

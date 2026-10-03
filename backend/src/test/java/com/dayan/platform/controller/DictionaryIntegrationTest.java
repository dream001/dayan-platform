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
class DictionaryIntegrationTest extends PostgreSqlIntegrationTestSupport {

    private static final String DICTIONARIES = "/api/v1/data/dictionaries";

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
    void managesSingleBatchAndExportedDictionaryEntries() throws Exception {
        long skillId = responseData(mockMvc.perform(post(DICTIONARIES)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dictionaryType": "SKILL",
                                  "scope": "GLOBAL",
                                  "projectId": null,
                                  "englishText": "pick up {A}",
                                  "chineseText": "拿起{A}",
                                  "japaneseText": "持ち上げる{A}"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scope").value("GLOBAL"))
                .andExpect(jsonPath("$.data.canEdit").value(true))
                .andReturn()).path("id").asLong();

        mockMvc.perform(post(DICTIONARIES)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dictionaryType": "SKILL",
                                  "scope": "GLOBAL",
                                  "englishText": "place {A}",
                                  "chineseText": "将{A}放入{B}"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_ARGUMENT"));

        mockMvc.perform(post(DICTIONARIES + "/batch")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dictionaryType": "OBJECT",
                                  "scope": "SHARED",
                                  "projectId": null,
                                  "content": "apple,苹果,りんご\\ncup,杯子,カップ"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(2));

        mockMvc.perform(get(DICTIONARIES)
                        .header("Authorization", bearer(adminToken))
                        .param("type", "OBJECT")
                        .param("sort", "ENGLISH")
                        .param("direction", "ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].englishText").value("apple"))
                .andExpect(jsonPath("$.data.items[1].englishText").value("cup"));

        MvcResult export = mockMvc.perform(get(DICTIONARIES + "/export")
                        .header("Authorization", bearer(adminToken))
                        .param("type", "OBJECT"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(export.getResponse().getContentType()).startsWith("text/csv");
        assertThat(export.getResponse().getContentAsString()).contains("apple", "苹果", "cup");

        mockMvc.perform(delete(DICTIONARIES + "/" + skillId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsGlobalDictionaryCreationByNonAdministrator() throws Exception {
        long userId = jdbcTemplate.queryForObject(
                """
                INSERT INTO sys_user (username, password_hash, display_name)
                VALUES ('dictionary-manager', ?, 'Dictionary Manager')
                RETURNING id
                """,
                Long.class,
                passwordEncoder.encode("Dictionary-Manager-Password-2026")
        );
        long managerRoleId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_role WHERE code = 'MANAGER'",
                Long.class
        );
        jdbcTemplate.update(
                "INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)",
                userId,
                managerRoleId
        );
        String token = login("dictionary-manager", "Dictionary-Manager-Password-2026");

        mockMvc.perform(post(DICTIONARIES)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dictionaryType": "OBJECT",
                                  "scope": "GLOBAL",
                                  "englishText": "apple",
                                  "chineseText": "苹果"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
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

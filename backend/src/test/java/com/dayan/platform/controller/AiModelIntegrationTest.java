package com.dayan.platform.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class AiModelIntegrationTest extends PostgreSqlIntegrationTestSupport {

    private static final String MODELS = "/api/v1/basic/models";

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
    void managesModelsWithoutExposingCredentials() throws Exception {
        MvcResult created = mockMvc.perform(post(MODELS)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(modelJson("doubao-seed-1-6", "access-value", "secret-value")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.manufacturer").value("豆包"))
                .andExpect(jsonPath("$.data.modelType").value("VIDEO"))
                .andExpect(jsonPath("$.data.accessAddress").value("http://127.0.0.1:9876"))
                .andExpect(jsonPath("$.data.accessKeyConfigured").value(true))
                .andExpect(jsonPath("$.data.secretKeyConfigured").value(true))
                .andExpect(jsonPath("$.data.accessKey").doesNotExist())
                .andExpect(jsonPath("$.data.secretKey").doesNotExist())
                .andReturn();
        long modelId = responseData(created).path("id").asLong();

        Map<String, Object> stored = jdbcTemplate.queryForMap(
                "SELECT access_key_ciphertext, secret_key_ciphertext FROM ai_model WHERE id = ?",
                modelId
        );
        assertThat(stored.get("access_key_ciphertext")).isNotEqualTo("access-value");
        assertThat(stored.get("secret_key_ciphertext")).isNotEqualTo("secret-value");

        mockMvc.perform(put(MODELS + "/" + modelId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(modelJson("doubao-seed-1-6-pro", "", "")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("doubao-seed-1-6-pro"))
                .andExpect(jsonPath("$.data.accessKeyConfigured").value(true))
                .andExpect(jsonPath("$.data.secretKeyConfigured").value(true));

        mockMvc.perform(post(MODELS + "/" + modelId + "/test")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(false))
                .andExpect(jsonPath("$.data.message").value(
                        "Connection failed: modelUrl must not resolve to a private or local address"
                ));

        mockMvc.perform(post(MODELS + "/" + modelId + "/debug")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"hello model\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(false))
                .andExpect(jsonPath("$.data.statusCode").value(0))
                .andExpect(jsonPath("$.data.message").value(
                        "Debug request failed: modelUrl must not resolve to a private or local address"
                ));

        mockMvc.perform(patch(MODELS + "/" + modelId + "/status")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));

        mockMvc.perform(delete(MODELS + "/" + modelId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM ai_model WHERE id = ?",
                Integer.class,
                modelId
        )).isZero();
    }

    private String modelJson(String name, String accessKey, String secretKey) {
        return """
                {
                  "manufacturer": "豆包",
                  "name": "%s",
                  "accessAddress": "https://ark.cn-beijing.volces.com",
                  "modelUrl": "http://127.0.0.1:9876/v1/chat/completions",
                  "modelType": "VIDEO",
                  "accessKey": "%s",
                  "secretKey": "%s",
                  "enabled": true
                }
                """.formatted(name, accessKey, secretKey);
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

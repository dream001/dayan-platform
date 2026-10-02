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
class AiAgentIntegrationTest extends PostgreSqlIntegrationTestSupport {

    private static final String AGENTS = "/api/v1/basic/agents";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminToken;
    private long modelId;

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
        modelId = jdbcTemplate.queryForObject(
                """
                INSERT INTO ai_model (
                    manufacturer, name, access_address, model_url, model_type, enabled
                )
                VALUES ('Integration', 'agent-chat-model', 'https://example.com',
                        'https://example.com/v1/chat/completions', 'CHAT', TRUE)
                RETURNING id
                """,
                Long.class
        );
        adminToken = login();
    }

    @Test
    void managesAgentLifecycleAndRejectsDebuggingWhenDisabled() throws Exception {
        long agentId = responseData(mockMvc.perform(post(AGENTS)
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(agentJson("Review assistant", "Review records precisely.")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.modelName").value("agent-chat-model"))
                .andExpect(jsonPath("$.data.temperature").value(0.4))
                .andReturn()).path("id").asLong();

        mockMvc.perform(get(AGENTS).header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].systemPrompt").value("Review records precisely."));

        mockMvc.perform(put(AGENTS + "/" + agentId)
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(agentJson("Review assistant", "Return strict JSON.")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.systemPrompt").value("Return strict JSON."));

        mockMvc.perform(patch(AGENTS + "/" + agentId + "/status")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));

        mockMvc.perform(post(AGENTS + "/" + agentId + "/debug")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Inspect this record\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMON_CONFLICT"));

        mockMvc.perform(delete(AGENTS + "/" + agentId).header("Authorization", bearer()))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM ai_agent WHERE id = ?",
                Integer.class,
                agentId
        )).isZero();
    }

    private String agentJson(String name, String prompt) {
        return """
                {
                  "name": "%s",
                  "description": "Integration Agent",
                  "systemPrompt": "%s",
                  "modelId": %d,
                  "temperature": 0.4,
                  "maxTokens": 1024,
                  "enabled": true
                }
                """.formatted(name, prompt, modelId);
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

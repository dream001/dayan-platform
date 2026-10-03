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
class MqttManagementIntegrationTest extends PostgreSqlIntegrationTestSupport {

    private static final String MQTT = "/api/v1/basic/mqtt";

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
        adminToken = login();
    }

    @Test
    void managesConnectionsSubscriptionsAndRealProbeResults() throws Exception {
        MvcResult createdConnection = mockMvc.perform(post(MQTT + "/connections")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Robot MQTT",
                                  "clientId": "dayan-integration",
                                  "brokerUrl": "tcp://127.0.0.1:1",
                                  "username": "robot",
                                  "password": "secret",
                                  "tlsEnabled": false,
                                  "cleanSession": true,
                                  "keepAliveSeconds": 30,
                                  "connectionTimeoutSeconds": 1,
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.credentialConfigured").value(true))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andReturn();
        long connectionId = data(createdConnection).path("id").asLong();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT password_ciphertext FROM mqtt_connection WHERE id = ?",
                String.class,
                connectionId
        )).isNotEqualTo("secret");

        mockMvc.perform(post(MQTT + "/subscriptions")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "connectionId": %d,
                                  "topicFilter": "robots/+/telemetry/#",
                                  "qos": 1,
                                  "description": "Robot telemetry",
                                  "enabled": true
                                }
                                """.formatted(connectionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.topicFilter").value("robots/+/telemetry/#"));

        mockMvc.perform(post(MQTT + "/connections/" + connectionId + "/test")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UNAVAILABLE"));

        mockMvc.perform(get(MQTT + "/overview").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connectionCount").isNumber())
                .andExpect(jsonPath("$.data.subscriptionCount").isNumber());
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
        return data(result).path("accessToken").asText();
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).path("data");
    }

    private String bearer() {
        return "Bearer " + adminToken;
    }
}

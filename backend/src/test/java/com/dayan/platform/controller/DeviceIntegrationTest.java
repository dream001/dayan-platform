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
class DeviceIntegrationTest extends PostgreSqlIntegrationTestSupport {

    private static final String DEVICES = "/api/v1/basic/devices";

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
    void registersReportsMonitorsAndSoftDeletesDevice() throws Exception {
        MvcResult registration = mockMvc.perform(post(DEVICES)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deviceCode": "integration-device-01",
                                  "remark": "Integration device",
                                  "robotId": null,
                                  "projectId": null
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.device.deviceCode").value("integration-device-01"))
                .andExpect(jsonPath("$.data.device.activated").value(false))
                .andReturn();
        JsonNode registered = responseData(registration);
        long id = registered.path("device").path("id").asLong();
        String agentId = registered.path("device").path("agentId").asText();
        String agentToken = registered.path("agentToken").asText();

        mockMvc.perform(post(DEVICES + "/" + id + "/install-command")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "pollIntervalSeconds": 30,
                                  "videoFps": 10,
                                  "rosDomainId": 8,
                                  "imageTopic": "/camera/image_raw",
                                  "maxVideoWidth": 1280,
                                  "remoteControlEnabled": true,
                                  "connectionPassword": "device-password",
                                  "verbose": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.command").value(
                        org.hamcrest.Matchers.containsString(agentId)
                ));

        mockMvc.perform(post(DEVICES + "/agent/report")
                        .header("X-Agent-Token", agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "hostname": "robot-edge-01",
                                  "operatingSystem": "Ubuntu 22.04",
                                  "platform": "linux/amd64",
                                  "kernelVersion": "6.8.0",
                                  "ipAddresses": ["10.20.0.8"],
                                  "cpuUsage": 21.5,
                                  "memoryUsage": 43.2,
                                  "diskUsage": 18.7,
                                  "cpuTemperature": 54.1,
                                  "memoryUsedBytes": 4294967296,
                                  "diskAvailableBytes": 107374182400,
                                  "activeTcpConnections": 12,
                                  "uptimeSeconds": 3600
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get(DEVICES + "/" + agentId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.device.activated").value(true))
                .andExpect(jsonPath("$.data.device.online").value(true))
                .andExpect(jsonPath("$.data.device.hostname").value("robot-edge-01"))
                .andExpect(jsonPath("$.data.metrics.length()").value(1))
                .andExpect(jsonPath("$.data.metrics[0].cpuUsage").value(21.5));

        mockMvc.perform(delete(DEVICES)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"deviceIds": [%d]}
                                """.formatted(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.succeeded").value(1))
                .andExpect(jsonPath("$.data.failed").value(0));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted FROM basic_device WHERE id = ?",
                Boolean.class,
                id
        )).isTrue();
    }

    @Test
    void rejectsInvalidDeviceCodeAndAgentToken() throws Exception {
        mockMvc.perform(post(DEVICES)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"deviceCode": "bad code", "remark": "", "robotId": null, "projectId": null}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(DEVICES + "/agent/report")
                        .header("X-Agent-Token", "64be70d2-ca90-4a8c-b3f1-42849ee8e381")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "hostname": "unknown",
                                  "operatingSystem": "Linux",
                                  "platform": "linux/amd64",
                                  "ipAddresses": [],
                                  "cpuUsage": 1,
                                  "memoryUsage": 1,
                                  "diskUsage": 1,
                                  "activeTcpConnections": 0,
                                  "uptimeSeconds": 0
                                }
                                """))
                .andExpect(status().isUnauthorized());
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

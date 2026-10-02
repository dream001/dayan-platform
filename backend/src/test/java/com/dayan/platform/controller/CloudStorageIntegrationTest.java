package com.dayan.platform.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

@SpringBootTest
@AutoConfigureMockMvc
class CloudStorageIntegrationTest extends PostgreSqlIntegrationTestSupport {

    private static final String STORAGES = "/api/v1/basic/storages";

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
    void managesEncryptedStorageConnectionsAndCollectsRealBucketUsage() throws Exception {
        mockMvc.perform(get(STORAGES).header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].storageKey").value("minio-default"))
                .andExpect(jsonPath("$.data[0].credentialConfigured").value(true))
                .andExpect(jsonPath("$.data[0].defaultStorage").value(true))
                .andExpect(jsonPath("$.data[0].secretKey").doesNotExist());

        long storageId = responseData(mockMvc.perform(post(STORAGES)
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(storageJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NEVER"))
                .andReturn()).path("id").asLong();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT secret_key_ciphertext FROM cloud_storage WHERE id = ?",
                String.class,
                storageId
        )).doesNotContain(MINIO_SECRET_KEY);

        mockMvc.perform(post(STORAGES + "/" + storageId + "/test")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.objectCount").isNumber())
                .andExpect(jsonPath("$.data.usageBytes").isNumber());

        mockMvc.perform(patch(STORAGES + "/" + storageId + "/default")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaultStorage").value(true));

        mockMvc.perform(delete(STORAGES + "/" + storageId)
                        .header("Authorization", bearer()))
                .andExpect(status().isConflict());
    }

    private String storageJson() {
        String endpoint = "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
        return """
                {
                  "storageKey": "integration-storage",
                  "name": "Integration storage",
                  "provider": "MINIO",
                  "endpoint": "%s",
                  "region": "",
                  "bucket": "%s",
                  "accessKey": "%s",
                  "secretKey": "%s",
                  "enabled": true
                }
                """.formatted(endpoint, MINIO_BUCKET, MINIO_ACCESS_KEY, MINIO_SECRET_KEY);
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

    private String bearer() {
        return "Bearer " + adminToken;
    }
}

package com.dayan.platform.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dayan.platform.support.PostgreSqlIntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@Import(AuthIntegrationTest.ForbiddenTestController.class)
class AuthIntegrationTest extends PostgreSqlIntegrationTestSupport {

    private static final String AUTH_PATH = "/api/v1/auth";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void resetAdministrator() {
        jdbcTemplate.update("DELETE FROM auth_session");
        jdbcTemplate.update(
                """
                UPDATE sys_user
                SET password_hash = ?,
                    display_name = 'Integration Administrator',
                    email = 'integration-admin@example.com',
                    phone = NULL,
                    enabled = TRUE,
                    password_changed_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE username = 'integration-admin'
                """,
                passwordEncoder.encode(INITIAL_ADMIN_PASSWORD)
        );
    }

    @Test
    void logsInWithBcryptAndReturnsRealPermissions() throws Exception {
        JsonNode tokens = login(INITIAL_ADMIN_PASSWORD);

        assertThat(tokens.path("accessToken").asText()).isNotBlank();
        assertThat(tokens.path("refreshToken").asText()).isNotBlank();
        assertThat(tokens.path("expiresIn").asLong()).isEqualTo(900);
        assertThat(tokens.path("user").path("permissions").toString())
                .contains("system:user:view", "file:upload");

        String storedHash = jdbcTemplate.queryForObject(
                "SELECT token_hash FROM auth_session",
                String.class
        );
        assertThat(storedHash)
                .hasSize(64)
                .doesNotContain(tokens.path("refreshToken").asText());
    }

    @Test
    void rejectsWrongPasswordAndDisabledUserWithSameError() throws Exception {
        assertInvalidCredentials("wrong-password");
        jdbcTemplate.update("UPDATE sys_user SET enabled = FALSE WHERE username = 'integration-admin'");
        assertInvalidCredentials(INITIAL_ADMIN_PASSWORD);
    }

    @Test
    void rotatesRefreshTokenAndRejectsReplay() throws Exception {
        JsonNode first = login(INITIAL_ADMIN_PASSWORD);
        String oldRefreshToken = first.path("refreshToken").asText();

        JsonNode rotated = responseData(mockMvc.perform(post(AUTH_PATH + "/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("refreshToken", oldRefreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andReturn());

        assertThat(rotated.path("refreshToken").asText()).isNotEqualTo(oldRefreshToken);
        mockMvc.perform(post(AUTH_PATH + "/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("refreshToken", oldRefreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_REFRESH_TOKEN"));
    }

    @Test
    void revokesRefreshTokenOnLogout() throws Exception {
        JsonNode tokens = login(INITIAL_ADMIN_PASSWORD);
        String accessToken = tokens.path("accessToken").asText();
        String refreshToken = tokens.path("refreshToken").asText();

        mockMvc.perform(post(AUTH_PATH + "/logout")
                        .header("Authorization", bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("refreshToken", refreshToken)))
                .andExpect(status().isOk());

        mockMvc.perform(post(AUTH_PATH + "/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("refreshToken", refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_REFRESH_TOKEN"));
    }

    @Test
    void returnsUnifiedUnauthorizedForbiddenAndDisabledAccountResponses() throws Exception {
        mockMvc.perform(get(AUTH_PATH + "/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        JsonNode tokens = login(INITIAL_ADMIN_PASSWORD);
        String accessToken = tokens.path("accessToken").asText();
        mockMvc.perform(get("/test/forbidden").header("Authorization", bearer(accessToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));

        jdbcTemplate.update("UPDATE sys_user SET enabled = FALSE WHERE username = 'integration-admin'");
        mockMvc.perform(get(AUTH_PATH + "/me").header("Authorization", bearer(accessToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    void supportsConfiguredCorsPreflight() throws Exception {
        mockMvc.perform(options(AUTH_PATH + "/login")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void readsAndUpdatesCurrentProfile() throws Exception {
        String accessToken = login(INITIAL_ADMIN_PASSWORD).path("accessToken").asText();

        mockMvc.perform(get(AUTH_PATH + "/me").header("Authorization", bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("integration-admin"))
                .andExpect(jsonPath("$.data.permissions.length()").value(29));

        mockMvc.perform(patch(AUTH_PATH + "/me/profile")
                        .header("Authorization", bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Updated Administrator",
                                  "email": "UPDATED@example.com",
                                  "phone": "+86 138 0000 0000"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("Updated Administrator"))
                .andExpect(jsonPath("$.data.email").value("updated@example.com"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT display_name FROM sys_user WHERE username = 'integration-admin'",
                String.class
        )).isEqualTo("Updated Administrator");
    }

    @Test
    void changesPasswordAndRevokesOnlyOtherRefreshSessions() throws Exception {
        JsonNode currentSession = login(INITIAL_ADMIN_PASSWORD);
        JsonNode otherSession = login(INITIAL_ADMIN_PASSWORD);
        String newPassword = "Changed-Admin-Password-2026";

        mockMvc.perform(put(AUTH_PATH + "/me/password")
                        .header("Authorization", bearer(currentSession.path("accessToken").asText()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "%s",
                                  "newPassword": "%s"
                                }
                                """.formatted(INITIAL_ADMIN_PASSWORD, newPassword)))
                .andExpect(status().isOk());

        mockMvc.perform(post(AUTH_PATH + "/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("refreshToken", otherSession.path("refreshToken").asText())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(AUTH_PATH + "/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("refreshToken", currentSession.path("refreshToken").asText())))
                .andExpect(status().isOk());

        assertInvalidCredentials(INITIAL_ADMIN_PASSWORD);
        assertThat(login(newPassword).path("accessToken").asText()).isNotBlank();
    }

    private JsonNode login(String password) throws Exception {
        MvcResult result = mockMvc.perform(post(AUTH_PATH + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"integration-admin","password":"%s"}
                                """.formatted(password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andReturn();
        return responseData(result);
    }

    private void assertInvalidCredentials(String password) throws Exception {
        mockMvc.perform(post(AUTH_PATH + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"integration-admin","password":"%s"}
                                """.formatted(password)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    private JsonNode responseData(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).path("data");
    }

    private String json(String field, String value) throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of(field, value));
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    @RestController
    static class ForbiddenTestController {

        @GetMapping("/test/forbidden")
        @PreAuthorize("hasAuthority('test:forbidden')")
        String forbidden() {
            return "not allowed";
        }
    }
}

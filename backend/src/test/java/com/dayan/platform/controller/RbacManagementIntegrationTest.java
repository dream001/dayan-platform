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
import java.util.LinkedHashMap;
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
class RbacManagementIntegrationTest extends PostgreSqlIntegrationTestSupport {

    private static final String SYSTEM = "/api/v1/system";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminToken;
    private long adminId;

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
        adminToken = login("integration-admin", INITIAL_ADMIN_PASSWORD).path("accessToken").asText();
    }

    @Test
    void rejectsUnauthenticatedAndUnauthorizedManagementRequests() throws Exception {
        mockMvc.perform(get(SYSTEM + "/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));

        long limitedRoleId = jdbcTemplate.queryForObject(
                """
                INSERT INTO sys_role (name, code)
                VALUES ('Limited viewer', 'LIMITED_VIEWER')
                RETURNING id
                """,
                Long.class
        );
        long viewPermissionId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu_permission WHERE code = 'system:user:view'",
                Long.class
        );
        jdbcTemplate.update(
                "INSERT INTO sys_role_permission (role_id, permission_id) VALUES (?, ?)",
                limitedRoleId,
                viewPermissionId
        );
        long limitedUserId = insertUser("limited-viewer", "Limited-Viewer-Password-2026");
        jdbcTemplate.update(
                "INSERT INTO sys_user_role (user_id, role_id, assigned_by) VALUES (?, ?, ?)",
                limitedUserId,
                limitedRoleId,
                adminId
        );
        String limitedToken = login(
                "limited-viewer",
                "Limited-Viewer-Password-2026"
        ).path("accessToken").asText();

        mockMvc.perform(get(SYSTEM + "/users").header("Authorization", bearer(limitedToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post(SYSTEM + "/users")
                        .header("Authorization", bearer(limitedToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userCreateJson("forbidden-created", null, limitedRoleId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    void performsDepartmentUserAndRoleCrudWithAggregatedDetails() throws Exception {
        long rootDepartmentId = createDepartment("Engineering", "engineering", null);
        long childDepartmentId = createDepartment("Platform", "platform", rootDepartmentId);
        long roleId = createRole("Platform operator", "PLATFORM_OPERATOR");
        long userId = responseData(mockMvc.perform(post(SYSTEM + "/users")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userCreateJson("platform-user", childDepartmentId, roleId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.departmentName").value("Platform"))
                .andExpect(jsonPath("$.data.roles[0].code").value("PLATFORM_OPERATOR"))
                .andReturn()).path("id").asLong();

        mockMvc.perform(get(SYSTEM + "/users")
                        .header("Authorization", bearer(adminToken))
                        .queryParam("keyword", "platform-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].roles[0].id").value(roleId));
        mockMvc.perform(get(SYSTEM + "/roles/" + roleId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userIds[0]").value(userId));

        mockMvc.perform(put(SYSTEM + "/users/" + userId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "departmentId": %d,
                                  "displayName": "Updated Platform User",
                                  "email": "updated-platform@example.com",
                                  "phone": "+86 138 0000 0000"
                                }
                                """.formatted(childDepartmentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("Updated Platform User"));

        mockMvc.perform(delete(SYSTEM + "/departments/" + rootDepartmentId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMON_CONFLICT"));
        mockMvc.perform(delete(SYSTEM + "/departments/" + childDepartmentId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict());
        mockMvc.perform(delete(SYSTEM + "/roles/" + roleId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict());
    }

    @Test
    void grantsMenuPermissionsAndReturnsCurrentDynamicMenuTree() throws Exception {
        long roleId = createRole("Report viewer", "REPORT_VIEWER");
        long userId = insertUser("report-viewer", "Report-Viewer-Password-2026");
        mockMvc.perform(put(SYSTEM + "/roles/" + roleId + "/users")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("ids", new long[]{userId}))))
                .andExpect(status().isOk());

        long menuId = responseData(mockMvc.perform(post(SYSTEM + "/menus")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentId": 1100,
                                  "type": "MENU",
                                  "name": "Reports",
                                  "code": "test:reports:view",
                                  "path": "/reports",
                                  "component": "reports/index",
                                  "icon": "report",
                                  "sortOrder": 90,
                                  "visible": true,
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()).path("id").asLong();
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM sys_role_permission rp
                JOIN sys_role r ON r.id = rp.role_id
                WHERE r.code = 'SUPER_ADMIN'
                  AND rp.permission_id = ?
                """,
                Integer.class,
                menuId
        )).isOne();

        mockMvc.perform(put(SYSTEM + "/roles/" + roleId + "/permissions")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("ids", new long[]{menuId}))))
                .andExpect(status().isOk());

        String userToken = login("report-viewer", "Report-Viewer-Password-2026")
                .path("accessToken").asText();
        mockMvc.perform(get("/api/v1/auth/me/menus")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("system:view"))
                .andExpect(jsonPath("$.data[0].children[0].code").value("test:reports:view"));

        mockMvc.perform(delete(SYSTEM + "/menus/" + menuId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict());
    }

    @Test
    void preservesAdministratorPermissionsAndReordersEverySiblingAtomically() throws Exception {
        long administratorRoleId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_role WHERE code = 'SUPER_ADMIN'",
                Long.class
        );
        mockMvc.perform(put(SYSTEM + "/roles/" + administratorRoleId + "/permissions")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[1000]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Administrator permissions are managed automatically"));

        long parentId = createMenu(null, "Sort parent", "test:sort-parent:view", 90);
        long firstId = createMenu(parentId, "First child", "test:first-child:view", 10);
        long secondId = createMenu(parentId, "Second child", "test:second-child:view", 20);

        mockMvc.perform(put(SYSTEM + "/menus/order")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "parentId", parentId,
                                "ids", new long[]{secondId, firstId}
                        ))))
                .andExpect(status().isOk());

        mockMvc.perform(get(SYSTEM + "/menus/" + parentId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.children[0].id").value(secondId))
                .andExpect(jsonPath("$.data.children[1].id").value(firstId));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT sort_order FROM sys_menu_permission WHERE id = ?",
                Integer.class,
                secondId
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT sort_order FROM sys_menu_permission WHERE id = ?",
                Integer.class,
                firstId
        )).isEqualTo(10);

        mockMvc.perform(put(SYSTEM + "/menus/order")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "parentId", parentId,
                                "ids", new long[]{firstId}
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_ARGUMENT"));
    }

    @Test
    void disablingAndResettingPasswordRevokeRefreshSessions() throws Exception {
        long userId = insertUser("session-user", "Session-User-Password-2026");
        JsonNode initialTokens = login("session-user", "Session-User-Password-2026");

        mockMvc.perform(patch(SYSTEM + "/users/" + userId + "/status")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk());
        assertRefreshRejected(initialTokens.path("refreshToken").asText());

        mockMvc.perform(patch(SYSTEM + "/users/" + userId + "/status")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk());
        JsonNode enabledTokens = login("session-user", "Session-User-Password-2026");
        mockMvc.perform(put(SYSTEM + "/users/" + userId + "/password")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"Reset-Session-Password-2026\"}"))
                .andExpect(status().isOk());
        assertRefreshRejected(enabledTokens.path("refreshToken").asText());
        assertThat(login("session-user", "Reset-Session-Password-2026")
                .path("accessToken").asText()).isNotBlank();
    }

    private long createDepartment(String name, String code, Long parentId) throws Exception {
        String parent = parentId == null ? "null" : parentId.toString();
        return responseData(mockMvc.perform(post(SYSTEM + "/departments")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentId": %s,
                                  "name": "%s",
                                  "code": "%s",
                                  "sortOrder": 10,
                                  "enabled": true
                                }
                                """.formatted(parent, name, code)))
                .andExpect(status().isOk())
                .andReturn()).path("id").asLong();
    }

    private long createRole(String name, String code) throws Exception {
        return responseData(mockMvc.perform(post(SYSTEM + "/roles")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "code": "%s",
                                  "description": "Integration test role",
                                  "enabled": true
                                }
                                """.formatted(name, code)))
                .andExpect(status().isOk())
                .andReturn()).path("role").path("id").asLong();
    }

    private long createMenu(Long parentId, String name, String code, int sortOrder) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("parentId", parentId);
        request.put("type", "MENU");
        request.put("name", name);
        request.put("code", code);
        request.put("path", "/" + code.replace(':', '-'));
        request.put("component", "test/index");
        request.put("icon", "menu");
        request.put("sortOrder", sortOrder);
        request.put("visible", true);
        request.put("enabled", true);
        return responseData(mockMvc.perform(post(SYSTEM + "/menus")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()).path("id").asLong();
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

    private String userCreateJson(String username, Long departmentId, long roleId) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("departmentId", departmentId);
        request.put("username", username);
        request.put("password", "Created-User-Password-2026");
        request.put("displayName", "Created User");
        request.put("email", username + "@example.com");
        request.put("phone", "");
        request.put("enabled", true);
        request.put("roleIds", new long[]{roleId});
        return objectMapper.writeValueAsString(request);
    }

    private JsonNode login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        return responseData(result);
    }

    private void assertRefreshRejected(String refreshToken) throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "refreshToken", refreshToken
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_REFRESH_TOKEN"));
    }

    private JsonNode responseData(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).path("data");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}

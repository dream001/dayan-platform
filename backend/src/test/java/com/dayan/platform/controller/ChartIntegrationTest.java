package com.dayan.platform.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dayan.platform.support.PostgreSqlIntegrationTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

@SpringBootTest(properties = {
        "app.environment=test",
        "app.version=test"
})
@AutoConfigureMockMvc
@Transactional
class ChartIntegrationTest extends PostgreSqlIntegrationTestSupport {

    private static final String CHARTS = "/api/v1/data/charts";
    private static final String PASSWORD = "Chart-Test-Password-2026";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private long userId;
    private String token;
    private long projectId;

    @BeforeEach
    void prepareChartData() throws Exception {
        jdbcTemplate.update("DELETE FROM auth_session");
        userId = jdbcTemplate.queryForObject(
                """
                INSERT INTO sys_user (username, password_hash, display_name)
                VALUES ('chart-annotator', ?, 'Chart Annotator')
                RETURNING id
                """,
                Long.class,
                passwordEncoder.encode(PASSWORD)
        );
        jdbcTemplate.update(
                """
                INSERT INTO sys_user_role (user_id, role_id)
                SELECT ?, id FROM sys_role WHERE code = 'ANNOTATOR'
                """,
                userId
        );
        projectId = insertProject("CHART_ACCESSIBLE", "Chart accessible");
        insertProjectMember(projectId);
        insertDictionary(projectId);
        long firstDataset = insertDataset(projectId, "chart-first");
        long secondDataset = insertDataset(projectId, "chart-second");
        insertAnnotation(firstDataset, "Pick", "Pick cube", "0", "2", 2);
        insertAnnotation(firstDataset, "Place", "Place cube", "2", "5", 1);
        insertAnnotation(firstDataset, "Pick", "Return hand", "5", "6", 0);
        insertAnnotation(secondDataset, "Pick", "Pick cube", "0", "1", 0);
        insertAnnotation(secondDataset, "Place", "Place cube", "1", "3", 0);
        token = login();
    }

    @Test
    void aggregatesAllFiveChartsFromProjectAnnotations() throws Exception {
        mockMvc.perform(get(CHARTS + "/projects")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(projectId));

        mockMvc.perform(get(CHARTS + "/relationships")
                        .header("Authorization", bearer())
                        .queryParam("projectId", String.valueOf(projectId))
                        .queryParam("locale", "zh-CN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("夹取"))
                .andExpect(jsonPath("$.data[0].value").value(5));

        mockMvc.perform(get(CHARTS + "/planning")
                        .header("Authorization", bearer())
                        .queryParam("projectId", String.valueOf(projectId))
                        .queryParam("locale", "zh-CN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodes[0]").value("夹取动作"))
                .andExpect(jsonPath("$.data.nodes").isArray())
                .andExpect(jsonPath("$.data.links.length()").value(2));

        mockMvc.perform(get(CHARTS + "/durations")
                        .header("Authorization", bearer())
                        .queryParam("projectId", String.valueOf(projectId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].action").value("Place"))
                .andExpect(jsonPath("$.data[0].averageSeconds").value(2.500));

        mockMvc.perform(get(CHARTS + "/dependencies")
                        .header("Authorization", bearer())
                        .queryParam("projectId", String.valueOf(projectId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.links[0].source").isNotEmpty());

        mockMvc.perform(get(CHARTS + "/calendar")
                        .header("Authorization", bearer())
                        .queryParam("projectId", String.valueOf(projectId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dailyAverage").value(1.67))
                .andExpect(jsonPath("$.data.points.length()").value(3));
    }

    @Test
    void rejectsAProjectOutsideTheCurrentUsersScope() throws Exception {
        long inaccessibleProject = insertProject("CHART_PRIVATE", "Chart private");

        mockMvc.perform(get(CHARTS + "/relationships")
                        .header("Authorization", bearer())
                        .queryParam("projectId", String.valueOf(inaccessibleProject)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    private long insertProject(String code, String name) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO basic_project
                    (code, name, project_type, access_level, storage_provider,
                     storage_quota_bytes, owner_id)
                VALUES (?, ?, 'TEAM', 'PRIVATE', 'MINIO', 1073741824, ?)
                RETURNING id
                """,
                Long.class,
                code,
                name,
                userId
        );
    }

    private void insertProjectMember(long id) {
        jdbcTemplate.update(
                """
                INSERT INTO basic_project_member
                    (project_id, user_id, role, data_access_level, assigned_by)
                VALUES (?, ?, 'ANNOTATOR', 'READ_WRITE', ?)
                """,
                id,
                userId,
                userId
        );
    }

    private void insertDictionary(long id) {
        jdbcTemplate.update(
                """
                INSERT INTO data_dictionary_entry
                    (dictionary_type, scope, project_id, english_text,
                     chinese_text, creator_id)
                VALUES ('SKILL', 'PROJECT', ?, 'grasp', '夹取', ?)
                """,
                id,
                userId
        );
        jdbcTemplate.update(
                """
                INSERT INTO data_dictionary_entry
                    (dictionary_type, scope, project_id, english_text,
                     chinese_text, creator_id)
                VALUES ('SKILL', 'PROJECT', ?, 'Pick', '夹取动作', ?)
                """,
                id,
                userId
        );
    }

    private long insertDataset(long id, String name) {
        long fileId = jdbcTemplate.queryForObject(
                """
                INSERT INTO file_metadata
                    (bucket_name, object_key, original_name, content_type, size_bytes, status)
                VALUES ('dayan-integration', ?, ?, 'video/mp4', 1024, 'READY')
                RETURNING id
                """,
                Long.class,
                "charts/" + name + ".mp4",
                name + ".mp4"
        );
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO data_dataset
                    (name, file_id, data_type, size_bytes, project_id, uploader_id)
                VALUES (?, ?, 'VIDEO', 1024, ?, ?)
                RETURNING id
                """,
                Long.class,
                name,
                fileId,
                id,
                userId
        );
    }

    private void insertAnnotation(
            long datasetId,
            String action,
            String description,
            String start,
            String end,
            int daysAgo
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO data_annotation
                    (dataset_id, annotator_id, content_text, covered_duration_seconds,
                     skill_name, skill_name_zh, object_a_name, object_a_name_zh,
                     object_b_name, object_b_name_zh, action_name,
                     start_offset_seconds, end_offset_seconds, created_at)
                VALUES (?, ?, ?, (?::numeric - ?::numeric), 'grasp', '抓取',
                        'cube', '方块', 'table', '桌面', ?, ?::numeric, ?::numeric, ?)
                """,
                datasetId,
                userId,
                description,
                end,
                start,
                action,
                start,
                end,
                OffsetDateTime.now(ZoneOffset.UTC).minusDays(daysAgo)
        );
    }

    private String login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "chart-annotator",
                                "password", PASSWORD
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray())
                .path("data")
                .path("accessToken")
                .asText();
    }

    private String bearer() {
        return "Bearer " + token;
    }
}

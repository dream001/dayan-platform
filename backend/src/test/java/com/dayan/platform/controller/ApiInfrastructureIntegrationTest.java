package com.dayan.platform.controller;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dayan.platform.dto.PageQuery;
import com.dayan.platform.support.PostgreSqlIntegrationTestSupport;
import jakarta.validation.Valid;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(properties = {
        "app.environment=integration",
        "spring.autoconfigure.exclude="
})
@AutoConfigureMockMvc
@Import(ApiInfrastructureIntegrationTest.ValidationTestController.class)
class ApiInfrastructureIntegrationTest extends PostgreSqlIntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void wrapsSuccessfulResponseAndPropagatesRequestId() throws Exception {
        mockMvc.perform(get("/api/v1/system/info").header("X-Request-Id", "request-123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "request-123"))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.requestId").value("request-123"))
                .andExpect(jsonPath("$.data.name").value("dayan-platform"))
                .andExpect(jsonPath("$.data.environment").value("integration"));
    }

    @Test
    void replacesUnsafeRequestId() throws Exception {
        mockMvc.perform(get("/api/v1/system/info").header("X-Request-Id", "unsafe request id"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", matchesPattern("[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.requestId", matchesPattern("[0-9a-f-]{36}")));
    }

    @Test
    void returnsStableFieldValidationError() throws Exception {
        mockMvc.perform(get("/test/page").queryParam("page", "0").queryParam("size", "201"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.data.page[0]").value("page must be at least 1"))
                .andExpect(jsonPath("$.data.size[0]").value("size must not exceed 200"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void returnsUnifiedNotFoundResponse() throws Exception {
        mockMvc.perform(get("/api/v1/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMON_RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @RestController
    static class ValidationTestController {

        @GetMapping("/test/page")
        PageQuery validate(@Valid @ModelAttribute PageQuery query) {
            return query;
        }
    }
}

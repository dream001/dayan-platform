package com.dayan.platform.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class QualityControlDtos {

    private QualityControlDtos() {
    }

    public enum RuleScope {
        GLOBAL,
        PROJECT
    }

    public enum AssertionType {
        NUMERIC,
        REQUIRED_TOPIC,
        FORBIDDEN_TOPIC
    }

    public enum AssertionSeverity {
        ERROR,
        WARNING
    }

    public enum MetricScope {
        ALL,
        TOPIC,
        SCHEMA
    }

    public record AssertionRequest(
            @NotNull AssertionType type,
            @Size(max = 100) String metric,
            @Size(max = 2) String operator,
            BigDecimal threshold,
            @NotNull AssertionSeverity severity,
            @NotNull MetricScope metricScope,
            @Size(max = 200) String matchPattern
    ) {
    }

    public record RuleRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 500) String description,
            @NotNull RuleScope scope,
            @Positive Long projectId,
            @NotBlank
            @Pattern(regexp = "MCAP|BAG|VIDEO|AUDIO|IMAGE|HDF5|LEROBOT|MEITUAN|"
                    + "LUMOS|ZC0TOUCH|SENSEXPERIENCE|BVH")
            String dataType,
            @NotBlank @Size(max = 200) String datasetPattern,
            @NotNull Boolean enabled,
            @NotNull Integer priority,
            @NotNull @Size(max = 100) List<@Valid AssertionRequest> assertions
    ) {
    }

    public record RunRequest(Set<@Positive Long> ruleIds) {
    }

    public record TopicMetricReport(
            @NotBlank @Size(max = 300) String topic,
            @NotBlank @Size(max = 300) String schema,
            @NotNull Map<String, BigDecimal> metrics
    ) {
    }

    public record ReportRequest(
            @NotNull Map<String, BigDecimal> globalMetrics,
            @NotNull List<@Valid TopicMetricReport> topics
    ) {
    }

    public record OverrideRequest(
            Boolean passed,
            @Size(max = 500) String reason
    ) {
    }

    public record LogFilter(
            Long projectId,
            Long datasetId,
            Long ruleId,
            String status,
            Boolean effectivePass,
            Boolean overridden,
            String keyword
    ) {
    }
}

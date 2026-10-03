package com.dayan.platform.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public final class WorkflowDtos {

    private WorkflowDtos() {
    }

    public enum Scope {
        GLOBAL,
        PROJECT
    }

    public enum LogicOperator {
        AND,
        OR
    }

    public record MatchCondition(
            @NotBlank @Size(max = 32) String field,
            @NotBlank @Size(max = 16) String operator,
            @Size(max = 500) String value,
            @Size(max = 8) String flags
    ) {
    }

    public record MatchRuleRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 500) String description,
            @NotNull Scope scope,
            @Positive Long projectId,
            @NotNull @Min(0) Integer priority,
            @NotNull Boolean enabled,
            @NotNull LogicOperator logicOperator,
            @NotNull @Size(min = 1, max = 50) List<@Valid MatchCondition> conditions
    ) {
    }

    public record ActionStep(
            @NotBlank @Size(max = 64) String action,
            @NotNull Map<String, Object> params
    ) {
    }

    public record ActionRuleRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 500) String description,
            @NotNull Scope scope,
            @Positive Long projectId,
            @NotNull Boolean enabled,
            @NotNull @Size(min = 1, max = 50) List<@Valid ActionStep> steps
    ) {
    }

    public record DefinitionRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 500) String description,
            @NotNull Scope scope,
            @Positive Long projectId,
            @NotNull @Min(0) Integer priority,
            @NotNull Boolean enabled,
            @NotNull @Positive Long matchRuleId,
            @NotNull @Positive Long actionRuleId
    ) {
    }

    public record TestRequest(
            @Positive Long projectId,
            @NotNull @Size(max = 20000) List<@Positive Long> datasetIds
    ) {
    }

    public record RunRequest(
            @NotNull @Positive Long workflowId,
            @NotNull @Positive Long datasetId
    ) {
    }

    public record PageFilter(
            @Min(1) int page,
            @Min(1) @Max(100) int size,
            Long projectId,
            Boolean enabled,
            String keyword
    ) {
    }
}

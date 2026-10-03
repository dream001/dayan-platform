package com.dayan.platform.service.impl;

import com.dayan.platform.common.api.ErrorCode;
import com.dayan.platform.common.exception.BusinessException;
import com.dayan.platform.dto.WorkflowDtos.ActionRuleRequest;
import com.dayan.platform.dto.WorkflowDtos.ActionStep;
import com.dayan.platform.dto.WorkflowDtos.DefinitionRequest;
import com.dayan.platform.dto.WorkflowDtos.MatchCondition;
import com.dayan.platform.dto.WorkflowDtos.MatchRuleRequest;
import com.dayan.platform.dto.WorkflowDtos.TestRequest;
import com.dayan.platform.repository.mapper.WorkflowMapper;
import com.dayan.platform.repository.query.WorkflowRows.ActionRuleRow;
import com.dayan.platform.repository.query.WorkflowRows.DatasetRow;
import com.dayan.platform.repository.query.WorkflowRows.DefinitionRow;
import com.dayan.platform.repository.query.WorkflowRows.MatchRuleRow;
import com.dayan.platform.repository.query.WorkflowRows.RunRow;
import com.dayan.platform.service.WorkflowService;
import com.dayan.platform.vo.WorkflowViews.ActionRuleView;
import com.dayan.platform.vo.WorkflowViews.DatasetOption;
import com.dayan.platform.vo.WorkflowViews.DefinitionView;
import com.dayan.platform.vo.WorkflowViews.MatchRuleView;
import com.dayan.platform.vo.WorkflowViews.MatchSample;
import com.dayan.platform.vo.WorkflowViews.Overview;
import com.dayan.platform.vo.WorkflowViews.ProjectOption;
import com.dayan.platform.vo.WorkflowViews.RunView;
import com.dayan.platform.vo.WorkflowViews.TestResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class WorkflowServiceImpl implements WorkflowService {

    private static final Set<String> MATCH_FIELDS = Set.of(
            "name", "extension_name", "robot_type", "sizemb",
            "source_url", "remote_url", "local_url"
    );
    private static final Set<String> MATCH_OPERATORS = Set.of(
            "eq", "contains", "regex", "gt", "lt", "gte", "lte",
            "is_null", "is_not_null"
    );
    private static final Set<String> ACTIONS = Set.of(
            "autoRename", "autoImportProject",
            "io_hdf5agilex2mcap", "io_hdf5realman2mcap", "io_hdf5dobot2mcap",
            "io_hdf5limx2mcap", "io_hdf5unix2mcap", "io_lerobot2mcap",
            "io_agibot2mcap", "io_meituan2mcap", "io_lumos2mcap",
            "io_zc0touch2mcap", "io_sensexperience2mcap", "io_bvh2mcap",
            "io_generateTransforms", "io_galbotproto2mcap", "runAlgorithm",
            "qualityCheck", "autoAnnotate", "exportDataset"
    );

    private final WorkflowMapper mapper;
    private final ObjectMapper objectMapper;

    public WorkflowServiceImpl(WorkflowMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Overview overview(long userId, boolean admin) {
        var row = mapper.selectOverview(userId, admin);
        List<DefinitionView> workflows = mapper.selectDefinitions(
                userId, admin, null, null, null
        ).stream().map(item -> definitionView(item, userId, admin)).toList();
        return new Overview(
                row.workflowCount(),
                row.enabledWorkflowCount(),
                row.matchRuleCount(),
                row.actionRuleCount(),
                row.queuedRunCount(),
                row.failedRunCount(),
                workflows
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<MatchRuleView> matchRules(
            Long projectId,
            Boolean enabled,
            String keyword,
            long userId,
            boolean admin
    ) {
        return mapper.selectMatchRules(
                userId, admin, projectId, enabled, normalize(keyword)
        ).stream().map(row -> matchRuleView(row, userId, admin)).toList();
    }

    @Override
    @Transactional
    public MatchRuleView saveMatchRule(
            Long id,
            MatchRuleRequest request,
            long userId,
            boolean admin
    ) {
        validateScope(request.scope().name(), request.projectId(), userId, admin);
        validateConditions(request.conditions());
        String description = normalize(request.description());
        String conditionsJson = write(request.conditions());
        long savedId;
        if (id == null) {
            savedId = mapper.insertMatchRule(
                    request.name().trim(),
                    description,
                    request.scope().name(),
                    projectId(request.scope().name(), request.projectId()),
                    request.priority(),
                    request.enabled(),
                    request.logicOperator().name(),
                    conditionsJson,
                    userId
            );
        } else {
            MatchRuleRow existing = requireMatchRule(id);
            requireManageable(existing.scope(), existing.projectId(), userId, admin);
            mapper.updateMatchRule(
                    id,
                    request.name().trim(),
                    description,
                    request.scope().name(),
                    projectId(request.scope().name(), request.projectId()),
                    request.priority(),
                    request.enabled(),
                    request.logicOperator().name(),
                    conditionsJson
            );
            savedId = id;
        }
        return matchRuleView(requireMatchRule(savedId), userId, admin);
    }

    @Override
    @Transactional(readOnly = true)
    public TestResult testMatchRule(
            long id,
            TestRequest request,
            long userId,
            boolean admin
    ) {
        MatchRuleRow rule = requireMatchRule(id);
        requireAccessible(rule.scope(), rule.projectId(), userId, admin);
        if (!admin && request.projectId() == null) {
            throw invalid("Project is required when testing a match rule");
        }
        List<DatasetRow> datasets = mapper.selectDatasets(
                userId,
                admin,
                request.projectId(),
                request.datasetIds().isEmpty() ? null : request.datasetIds()
        );
        List<MatchCondition> conditions = readConditions(rule.conditionsJson());
        List<DatasetRow> matched = datasets.stream()
                .filter(dataset -> matches(dataset, conditions, rule.logicOperator()))
                .toList();
        List<MatchSample> samples = matched.stream()
                .limit(50)
                .map(dataset -> new MatchSample(
                        dataset.id(),
                        dataset.name(),
                        dataset.dataType(),
                        dataset.projectName()
                ))
                .toList();
        return new TestResult(datasets.size(), matched.size(), samples);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActionRuleView> actionRules(
            Long projectId,
            Boolean enabled,
            String keyword,
            long userId,
            boolean admin
    ) {
        return mapper.selectActionRules(
                userId, admin, projectId, enabled, normalize(keyword)
        ).stream().map(row -> actionRuleView(row, userId, admin)).toList();
    }

    @Override
    @Transactional
    public ActionRuleView saveActionRule(
            Long id,
            ActionRuleRequest request,
            long userId,
            boolean admin
    ) {
        validateScope(request.scope().name(), request.projectId(), userId, admin);
        validateSteps(request.steps());
        String description = normalize(request.description());
        String stepsJson = write(request.steps());
        long savedId;
        if (id == null) {
            savedId = mapper.insertActionRule(
                    request.name().trim(),
                    description,
                    request.scope().name(),
                    projectId(request.scope().name(), request.projectId()),
                    request.enabled(),
                    stepsJson,
                    userId
            );
        } else {
            ActionRuleRow existing = requireActionRule(id);
            requireManageable(existing.scope(), existing.projectId(), userId, admin);
            mapper.updateActionRule(
                    id,
                    request.name().trim(),
                    description,
                    request.scope().name(),
                    projectId(request.scope().name(), request.projectId()),
                    request.enabled(),
                    stepsJson
            );
            savedId = id;
        }
        return actionRuleView(requireActionRule(savedId), userId, admin);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DefinitionView> definitions(
            Long projectId,
            Boolean enabled,
            String keyword,
            long userId,
            boolean admin
    ) {
        return mapper.selectDefinitions(
                userId, admin, projectId, enabled, normalize(keyword)
        ).stream().map(row -> definitionView(row, userId, admin)).toList();
    }

    @Override
    @Transactional
    public DefinitionView saveDefinition(
            Long id,
            DefinitionRequest request,
            long userId,
            boolean admin
    ) {
        String scope = request.scope().name();
        Long projectId = projectId(scope, request.projectId());
        validateScope(scope, projectId, userId, admin);
        MatchRuleRow matchRule = requireMatchRule(request.matchRuleId());
        ActionRuleRow actionRule = requireActionRule(request.actionRuleId());
        requireCompatible(scope, projectId, matchRule.scope(), matchRule.projectId(), "match");
        requireCompatible(scope, projectId, actionRule.scope(), actionRule.projectId(), "action");
        if (!matchRule.enabled() || !actionRule.enabled()) {
            throw invalid("Workflow rules must be enabled");
        }
        long savedId;
        if (id == null) {
            savedId = mapper.insertDefinition(
                    request.name().trim(),
                    normalize(request.description()),
                    scope,
                    projectId,
                    request.priority(),
                    request.enabled(),
                    request.matchRuleId(),
                    request.actionRuleId(),
                    userId
            );
        } else {
            DefinitionRow existing = requireDefinition(id);
            requireManageable(existing.scope(), existing.projectId(), userId, admin);
            mapper.updateDefinition(
                    id,
                    request.name().trim(),
                    normalize(request.description()),
                    scope,
                    projectId,
                    request.priority(),
                    request.enabled(),
                    request.matchRuleId(),
                    request.actionRuleId()
            );
            savedId = id;
        }
        return definitionView(requireDefinition(savedId), userId, admin);
    }

    @Override
    @Transactional
    public void delete(String resource, long id, long userId, boolean admin) {
        switch (resource) {
            case "match-rules" -> {
                MatchRuleRow rule = requireMatchRule(id);
                requireManageable(rule.scope(), rule.projectId(), userId, admin);
                if (mapper.countDefinitionReferences(id) > 0) {
                    throw conflict("Match rule is used by a workflow");
                }
                mapper.deleteMatchRule(id);
            }
            case "action-rules" -> {
                ActionRuleRow rule = requireActionRule(id);
                requireManageable(rule.scope(), rule.projectId(), userId, admin);
                if (mapper.countDefinitionReferences(id) > 0) {
                    throw conflict("Action rule is used by a workflow");
                }
                mapper.deleteActionRule(id);
            }
            case "definitions" -> {
                DefinitionRow definition = requireDefinition(id);
                requireManageable(
                        definition.scope(), definition.projectId(), userId, admin
                );
                mapper.deleteDefinition(id);
            }
            default -> throw invalid("Unsupported workflow resource");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectOption> projects(long userId, boolean admin) {
        return mapper.selectProjects(userId, admin);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DatasetOption> datasets(
            Long projectId,
            long userId,
            boolean admin
    ) {
        return mapper.selectDatasets(userId, admin, projectId, null).stream()
                .limit(500)
                .map(row -> new DatasetOption(
                        row.id(),
                        row.name(),
                        row.dataType(),
                        row.projectId(),
                        row.projectName()
                ))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DatasetOption> matchingDatasets(
            long workflowId,
            long userId,
            boolean admin
    ) {
        DefinitionRow workflow = requireDefinition(workflowId);
        requireAccessible(workflow.scope(), workflow.projectId(), userId, admin);
        MatchRuleRow rule = requireMatchRule(workflow.matchRuleId());
        List<MatchCondition> conditions = readConditions(rule.conditionsJson());
        Long projectId = "PROJECT".equals(workflow.scope()) ? workflow.projectId() : null;
        return mapper.selectDatasets(userId, admin, projectId, null).stream()
                .filter(dataset -> matches(dataset, conditions, rule.logicOperator()))
                .limit(500)
                .map(row -> new DatasetOption(
                        row.id(),
                        row.name(),
                        row.dataType(),
                        row.projectId(),
                        row.projectName()
                ))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RunView> runs(
            Long projectId,
            String status,
            long userId,
            boolean admin
    ) {
        String normalizedStatus = normalize(status);
        if (normalizedStatus != null) {
            normalizedStatus = normalizedStatus.toUpperCase(Locale.ROOT);
            if (!Set.of("QUEUED", "RUNNING", "COMPLETED", "FAILED", "CANCELLED")
                    .contains(normalizedStatus)) {
                throw invalid("Unsupported run status");
            }
        }
        return mapper.selectRuns(userId, admin, projectId, normalizedStatus)
                .stream().map(this::runView).toList();
    }

    @Override
    @Transactional
    public RunView startRun(
            long workflowId,
            long datasetId,
            long userId,
            boolean admin
    ) {
        DefinitionRow workflow = requireDefinition(workflowId);
        requireAccessible(workflow.scope(), workflow.projectId(), userId, admin);
        if (!workflow.enabled()) {
            throw conflict("Workflow is disabled");
        }
        List<DatasetRow> datasets = mapper.selectDatasets(
                userId, admin, null, List.of(datasetId)
        );
        if (datasets.isEmpty()) {
            throw notFound("Dataset not found or inaccessible");
        }
        DatasetRow dataset = datasets.getFirst();
        if ("PROJECT".equals(workflow.scope())
                && !workflow.projectId().equals(dataset.projectId())) {
            throw invalid("Dataset does not belong to the workflow project");
        }
        MatchRuleRow rule = requireMatchRule(workflow.matchRuleId());
        if (!matches(dataset, readConditions(rule.conditionsJson()), rule.logicOperator())) {
            throw invalid("Dataset does not match this workflow");
        }
        long runId = mapper.insertRun(
                workflowId,
                datasetId,
                "TEST",
                workflow.stepsJson(),
                userId
        );
        return runView(mapper.selectRun(runId));
    }

    private void validateScope(String scope, Long projectId, long userId, boolean admin) {
        if ("GLOBAL".equals(scope)) {
            if (!admin) {
                throw forbidden("Only administrators can manage global workflows");
            }
            return;
        }
        if (projectId == null) {
            throw invalid("Project is required for project scope");
        }
        if (!admin && mapper.countManagedProject(projectId, userId) == 0) {
            throw forbidden("Project management access is required");
        }
    }

    private void validateConditions(List<MatchCondition> conditions) {
        for (MatchCondition condition : conditions) {
            if (!MATCH_FIELDS.contains(condition.field())) {
                throw invalid("Unsupported match field: " + condition.field());
            }
            if (!MATCH_OPERATORS.contains(condition.operator())) {
                throw invalid("Unsupported match operator: " + condition.operator());
            }
            if (!condition.operator().startsWith("is_")
                    && !StringUtils.hasText(condition.value())) {
                throw invalid("Match condition value is required");
            }
            if ("regex".equals(condition.operator())) {
                try {
                    compileRegex(condition.value(), condition.flags());
                } catch (PatternSyntaxException exception) {
                    throw invalid("Invalid regular expression");
                }
            }
        }
    }

    private void validateSteps(List<ActionStep> steps) {
        for (ActionStep step : steps) {
            if (!ACTIONS.contains(step.action())) {
                throw invalid("Unsupported workflow action: " + step.action());
            }
        }
    }

    private boolean matches(
            DatasetRow dataset,
            List<MatchCondition> conditions,
            String logicOperator
    ) {
        if ("OR".equals(logicOperator)) {
            return conditions.stream().anyMatch(condition -> matches(dataset, condition));
        }
        return conditions.stream().allMatch(condition -> matches(dataset, condition));
    }

    private boolean matches(DatasetRow dataset, MatchCondition condition) {
        String actual = switch (condition.field()) {
            case "name" -> dataset.name();
            case "extension_name" -> extension(dataset.originalName());
            case "robot_type" -> dataset.robotCode();
            case "sizemb" -> String.valueOf(dataset.sizeBytes() / 1024.0 / 1024.0);
            case "source_url", "remote_url", "local_url" -> dataset.objectKey();
            default -> null;
        };
        return switch (condition.operator()) {
            case "is_null" -> !StringUtils.hasText(actual);
            case "is_not_null" -> StringUtils.hasText(actual);
            case "eq" -> actual != null && actual.equalsIgnoreCase(condition.value());
            case "contains" -> actual != null && actual.toLowerCase(Locale.ROOT)
                    .contains(condition.value().toLowerCase(Locale.ROOT));
            case "regex" -> actual != null
                    && compileRegex(condition.value(), condition.flags()).matcher(actual).find();
            case "gt", "lt", "gte", "lte" ->
                    compareNumber(actual, condition.value(), condition.operator());
            default -> false;
        };
    }

    private Pattern compileRegex(String value, String flags) {
        int options = 0;
        if (flags != null) {
            if (flags.contains("i")) {
                options |= Pattern.CASE_INSENSITIVE;
            }
            if (flags.contains("m")) {
                options |= Pattern.MULTILINE;
            }
            if (flags.contains("s")) {
                options |= Pattern.DOTALL;
            }
            if (flags.contains("u")) {
                options |= Pattern.UNICODE_CASE;
            }
        }
        return Pattern.compile(value, options);
    }

    private boolean compareNumber(String actual, String expected, String operator) {
        try {
            int result = Double.compare(Double.parseDouble(actual), Double.parseDouble(expected));
            return switch (operator) {
                case "gt" -> result > 0;
                case "lt" -> result < 0;
                case "gte" -> result >= 0;
                case "lte" -> result <= 0;
                default -> false;
            };
        } catch (NumberFormatException | NullPointerException exception) {
            return false;
        }
    }

    private String extension(String name) {
        if (name == null || !name.contains(".")) {
            return null;
        }
        return name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private void requireCompatible(
            String workflowScope,
            Long workflowProjectId,
            String ruleScope,
            Long ruleProjectId,
            String type
    ) {
        if ("GLOBAL".equals(workflowScope) && !"GLOBAL".equals(ruleScope)) {
            throw invalid("Global workflow requires a global " + type + " rule");
        }
        if ("PROJECT".equals(ruleScope) && !workflowProjectId.equals(ruleProjectId)) {
            throw invalid("Workflow and " + type + " rule must use the same project");
        }
    }

    private void requireAccessible(
            String scope,
            Long projectId,
            long userId,
            boolean admin
    ) {
        if (!admin && "PROJECT".equals(scope)
                && mapper.countManagedProject(projectId, userId) == 0) {
            throw forbidden("Workflow is not accessible");
        }
    }

    private void requireManageable(
            String scope,
            Long projectId,
            long userId,
            boolean admin
    ) {
        if ("GLOBAL".equals(scope)) {
            if (!admin) {
                throw forbidden("Only administrators can manage global workflows");
            }
        } else if (!admin && mapper.countManagedProject(projectId, userId) == 0) {
            throw forbidden("Project management access is required");
        }
    }

    private MatchRuleRow requireMatchRule(long id) {
        MatchRuleRow row = mapper.selectMatchRule(id);
        if (row == null) {
            throw notFound("Match rule not found");
        }
        return row;
    }

    private ActionRuleRow requireActionRule(long id) {
        ActionRuleRow row = mapper.selectActionRule(id);
        if (row == null) {
            throw notFound("Action rule not found");
        }
        return row;
    }

    private DefinitionRow requireDefinition(long id) {
        DefinitionRow row = mapper.selectDefinition(id);
        if (row == null) {
            throw notFound("Workflow not found");
        }
        return row;
    }

    private MatchRuleView matchRuleView(MatchRuleRow row, long userId, boolean admin) {
        return new MatchRuleView(
                row.id(),
                row.name(),
                row.description(),
                row.scope(),
                row.projectId(),
                row.projectName(),
                row.priority(),
                row.enabled(),
                row.logicOperator(),
                readConditions(row.conditionsJson()),
                canManage(row.scope(), row.projectId(), userId, admin),
                row.updatedAt()
        );
    }

    private ActionRuleView actionRuleView(ActionRuleRow row, long userId, boolean admin) {
        return new ActionRuleView(
                row.id(),
                row.name(),
                row.description(),
                row.scope(),
                row.projectId(),
                row.projectName(),
                row.enabled(),
                readSteps(row.stepsJson()),
                canManage(row.scope(), row.projectId(), userId, admin),
                row.updatedAt()
        );
    }

    private DefinitionView definitionView(DefinitionRow row, long userId, boolean admin) {
        List<ActionStep> steps = readSteps(row.stepsJson());
        return new DefinitionView(
                row.id(),
                row.name(),
                row.description(),
                row.scope(),
                row.projectId(),
                row.projectName(),
                row.priority(),
                row.enabled(),
                row.matchRuleId(),
                row.matchRuleName(),
                row.actionRuleId(),
                row.actionRuleName(),
                steps.size(),
                canManage(row.scope(), row.projectId(), userId, admin),
                row.updatedAt()
        );
    }

    private RunView runView(RunRow row) {
        return new RunView(
                row.id(),
                row.workflowId(),
                row.workflowName(),
                row.datasetId(),
                row.datasetName(),
                row.projectId(),
                row.projectName(),
                row.stage(),
                row.status(),
                row.progress(),
                row.triggerType(),
                readSteps(row.stepsSnapshotJson()),
                row.errorMessage(),
                row.startedAt(),
                row.completedAt(),
                row.createdAt()
        );
    }

    private boolean canManage(String scope, Long projectId, long userId, boolean admin) {
        return admin || ("PROJECT".equals(scope)
                && mapper.countManagedProject(projectId, userId) > 0);
    }

    private Long projectId(String scope, Long projectId) {
        return "GLOBAL".equals(scope) ? null : projectId;
    }

    private List<MatchCondition> readConditions(String json) {
        return read(json, new TypeReference<>() {
        });
    }

    private List<ActionStep> readSteps(String json) {
        return read(json, new TypeReference<>() {
        });
    }

    private <T> T read(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored workflow JSON is invalid", exception);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Workflow JSON could not be serialized", exception);
        }
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_ARGUMENT, message);
    }

    private BusinessException notFound(String message) {
        return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, message);
    }

    private BusinessException forbidden(String message) {
        return new BusinessException(ErrorCode.FORBIDDEN, message);
    }

    private BusinessException conflict(String message) {
        return new BusinessException(ErrorCode.CONFLICT, message);
    }
}

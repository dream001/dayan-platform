package com.dayan.platform.service.impl;

import com.dayan.platform.common.api.ErrorCode;
import com.dayan.platform.common.exception.BusinessException;
import com.dayan.platform.dto.QualityControlDtos.AssertionRequest;
import com.dayan.platform.dto.QualityControlDtos.AssertionType;
import com.dayan.platform.dto.QualityControlDtos.LogFilter;
import com.dayan.platform.dto.QualityControlDtos.MetricScope;
import com.dayan.platform.dto.QualityControlDtos.OverrideRequest;
import com.dayan.platform.dto.QualityControlDtos.ReportRequest;
import com.dayan.platform.dto.QualityControlDtos.RuleRequest;
import com.dayan.platform.dto.QualityControlDtos.RuleScope;
import com.dayan.platform.dto.QualityControlDtos.TopicMetricReport;
import com.dayan.platform.model.QualityExecution;
import com.dayan.platform.model.QualityRule;
import com.dayan.platform.repository.mapper.QualityControlMapper;
import com.dayan.platform.repository.query.QualityControlRows.ExecutionRow;
import com.dayan.platform.repository.query.QualityControlRows.OverviewRow;
import com.dayan.platform.repository.query.QualityControlRows.RuleRow;
import com.dayan.platform.service.QualityControlService;
import com.dayan.platform.vo.PageResponse;
import com.dayan.platform.vo.QualityControlViews.AssertionResult;
import com.dayan.platform.vo.QualityControlViews.DatasetOption;
import com.dayan.platform.vo.QualityControlViews.ExecutionDetail;
import com.dayan.platform.vo.QualityControlViews.ExecutionView;
import com.dayan.platform.vo.QualityControlViews.Overview;
import com.dayan.platform.vo.QualityControlViews.ProjectOption;
import com.dayan.platform.vo.QualityControlViews.RuleView;
import com.dayan.platform.vo.QualityControlViews.RunResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class QualityControlServiceImpl implements QualityControlService {

    private static final Set<String> DATA_TYPES = Set.of(
            "MCAP", "BAG", "VIDEO", "AUDIO", "IMAGE", "HDF5",
            "LEROBOT", "MEITUAN", "LUMOS", "ZC0TOUCH",
            "SENSEXPERIENCE", "BVH"
    );
    private static final Set<String> GENERIC_METRICS = Set.of(
            "file_size_bytes",
            "checksum_valid",
            "decode_error_count"
    );
    private static final Set<String> GLOBAL_METRICS = Set.of(
            "file_size_bytes",
            "checksum_valid",
            "decode_error_count",
            "record_duration_sec",
            "timestamp_monotonic_violations",
            "frame_rate",
            "frame_gap_median_ms",
            "frame_gap_p95_ms",
            "frame_gap_p99_ms",
            "frame_gap_max_ms",
            "drop_frame_count",
            "cross_topic_sync_p95_ms",
            "cross_topic_sync_p99_ms",
            "cross_topic_sync_max_ms",
            "leading_joint_still_sec",
            "trailing_joint_still_sec",
            "blur_score_p90",
            "exposure_outlier_ratio"
    );
    private static final Set<String> TOPIC_METRICS = Set.of(
            "frequency_hz",
            "topic_frame_gap_max_ms",
            "message_count",
            "duration_sec",
            "first_ts_sec",
            "last_ts_sec"
    );

    private final QualityControlMapper mapper;
    private final ObjectMapper objectMapper;

    public QualityControlServiceImpl(QualityControlMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RuleView> rules(
            int page,
            int size,
            Long projectId,
            Boolean enabled,
            String keyword,
            long userId,
            boolean admin
    ) {
        String normalizedKeyword = normalize(keyword);
        long total = mapper.countRules(
                userId,
                admin,
                projectId,
                enabled,
                normalizedKeyword
        );
        List<RuleView> items = mapper.selectRulePage(
                userId,
                admin,
                projectId,
                enabled,
                normalizedKeyword,
                (long) (page - 1) * size,
                size
        ).stream().map(row -> ruleView(row, userId, admin)).toList();
        return PageResponse.of(page, size, total, items);
    }

    @Override
    @Transactional
    public RuleView createRule(RuleRequest request, long userId, boolean admin) {
        validateRule(request, userId, admin);
        QualityRule rule = new QualityRule();
        rule.setCreatorId(userId);
        apply(rule, request);
        mapper.insert(rule);
        return ruleView(requireRule(rule.getId()), userId, admin);
    }

    @Override
    @Transactional
    public RuleView updateRule(
            long id,
            RuleRequest request,
            long userId,
            boolean admin
    ) {
        RuleRow existing = requireRule(id);
        requireRuleManagement(existing, userId, admin);
        validateRule(request, userId, admin);
        if ("GLOBAL".equals(existing.getScope()) && request.scope() != RuleScope.GLOBAL) {
            throw conflict("Global rule scope cannot be changed");
        }
        apply(existing, request);
        existing.setUpdatedAt(now());
        mapper.updateById(existing);
        return ruleView(requireRule(id), userId, admin);
    }

    @Override
    @Transactional
    public void deleteRule(long id, long userId, boolean admin) {
        RuleRow rule = requireRule(id);
        requireRuleManagement(rule, userId, admin);
        mapper.deleteRule(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectOption> projectOptions(long userId, boolean admin) {
        return mapper.selectProjects(userId, admin);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DatasetOption> datasetOptions(long userId, boolean admin) {
        return mapper.selectDatasets(userId, admin).stream()
                .map(row -> new DatasetOption(
                        row.getId(),
                        row.getName(),
                        row.getProjectName(),
                        row.getDataType()
                ))
                .toList();
    }

    @Override
    @Transactional
    public RunResult run(
            long datasetId,
            Set<Long> ruleIds,
            long userId,
            boolean admin
    ) {
        if (mapper.selectAccessibleDataset(datasetId, userId, admin) == null) {
            throw notFound("Dataset not found or inaccessible");
        }
        List<RuleRow> matching = mapper.selectMatchingRules(datasetId);
        Set<Long> selected = ruleIds == null ? Set.of() : new HashSet<>(ruleIds);
        if (!selected.isEmpty()
                && matching.stream().map(RuleRow::getId).filter(selected::contains).count()
                != selected.size()) {
            throw invalid("Selected rules do not match this dataset");
        }
        int queued = 0;
        int skipped = 0;
        for (RuleRow rule : matching) {
            if (!selected.isEmpty() && !selected.contains(rule.getId())) {
                continue;
            }
            QualityExecution execution = new QualityExecution();
            execution.setRuleId(rule.getId());
            execution.setDatasetId(datasetId);
            execution.setStatus("QUEUED");
            execution.setProgress(0);
            execution.setTriggerType("MANUAL");
            execution.setCreatedBy(userId);
            try {
                mapper.insertExecution(execution);
                queued++;
            } catch (DataIntegrityViolationException exception) {
                skipped++;
            }
        }
        return new RunResult(queued, skipped);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ExecutionView> logs(
            int page,
            int size,
            LogFilter filter,
            long userId,
            boolean admin
    ) {
        String status = upper(filter.status());
        String keyword = normalize(filter.keyword());
        long total = mapper.countExecutions(
                userId,
                admin,
                filter.projectId(),
                filter.datasetId(),
                filter.ruleId(),
                status,
                filter.effectivePass(),
                filter.overridden(),
                keyword
        );
        List<ExecutionView> items = mapper.selectExecutionPage(
                userId,
                admin,
                filter.projectId(),
                filter.datasetId(),
                filter.ruleId(),
                status,
                filter.effectivePass(),
                filter.overridden(),
                keyword,
                (long) (page - 1) * size,
                size
        ).stream().map(this::executionView).toList();
        return PageResponse.of(page, size, total, items);
    }

    @Override
    @Transactional(readOnly = true)
    public ExecutionDetail execution(long id, long userId, boolean admin) {
        ExecutionRow row = requireExecution(id, userId, admin);
        return new ExecutionDetail(executionView(row), readReport(row.getReportJson()));
    }

    @Override
    @Transactional
    public ExecutionDetail complete(
            long id,
            ReportRequest report,
            long userId,
            boolean admin
    ) {
        ExecutionRow execution = requireExecution(id, userId, admin);
        if (!Set.of("QUEUED", "RUNNING").contains(execution.getStatus())) {
            throw conflict("Only queued or running executions can be completed");
        }
        RuleRow rule = requireRule(execution.getRuleId());
        List<AssertionResult> results = evaluate(assertions(rule), report);
        boolean passed = results.stream().noneMatch(result ->
                !result.passed() && result.assertion().severity().name().equals("ERROR")
        );
        OffsetDateTime completedAt = now();
        mapper.completeExecution(
                id,
                passed ? "PASSED" : "FAILED",
                passed,
                write(report),
                write(results),
                completedAt
        );
        return execution(id, userId, admin);
    }

    @Override
    @Transactional
    public ExecutionView override(
            long id,
            OverrideRequest request,
            long userId,
            boolean admin
    ) {
        ExecutionRow execution = requireExecution(id, userId, admin);
        if (!Set.of("PASSED", "FAILED").contains(execution.getStatus())) {
            throw conflict("Only completed executions can be overridden");
        }
        if (!admin && (execution.getProjectId() == null
                || mapper.countManagedProject(execution.getProjectId(), userId) == 0)) {
            throw forbidden("Project management access is required");
        }
        OffsetDateTime now = now();
        if (request.passed() == null) {
            mapper.clearOverride(id, now);
        } else {
            String reason = normalize(request.reason());
            if (reason == null) {
                throw invalid("Override reason is required");
            }
            mapper.overrideExecution(id, request.passed(), reason, userId, now);
        }
        return executionView(requireExecution(id, userId, admin));
    }

    @Override
    @Transactional(readOnly = true)
    public Overview overview(long userId, boolean admin) {
        OverviewRow row = mapper.selectOverview(userId, admin);
        return new Overview(
                value(row.getTotalRules()),
                value(row.getEnabledRules()),
                value(row.getQueued()),
                value(row.getPassed()),
                value(row.getFailed()),
                value(row.getOverridden()),
                admin
        );
    }

    private void validateRule(RuleRequest request, long userId, boolean admin) {
        if (request.scope() == RuleScope.GLOBAL) {
            if (!admin) {
                throw forbidden("Only administrators can manage global quality rules");
            }
            if (request.projectId() != null) {
                throw invalid("Global rules cannot belong to a project");
            }
        } else {
            if (request.projectId() == null) {
                throw invalid("Project quality rules require a project");
            }
            if (!admin && mapper.countManagedProject(request.projectId(), userId) == 0) {
                throw forbidden("Project management access is required");
            }
        }
        String pattern = request.datasetPattern().trim();
        if (pattern.contains("%") || pattern.contains("_")) {
            throw invalid("Dataset pattern supports only * and ? wildcards");
        }
        String dataType = upper(request.dataType());
        if (!DATA_TYPES.contains(dataType)) {
            throw invalid("Unsupported quality data type");
        }
        for (AssertionRequest assertion : request.assertions()) {
            validateAssertion(assertion, dataType);
        }
    }

    private void validateAssertion(AssertionRequest assertion, String dataType) {
        if (!"MCAP".equals(dataType)
                && (assertion.type() != AssertionType.NUMERIC
                || assertion.metricScope() != MetricScope.ALL)) {
            throw invalid("Non-MCAP datasets support only global numeric assertions");
        }
        if (assertion.type() == AssertionType.NUMERIC) {
            String metric = normalize(assertion.metric());
            if (metric == null || assertion.threshold() == null
                    || !Set.of("<=", ">=", "<", ">", "==", "!=").contains(assertion.operator())) {
                throw invalid("Numeric assertions require a metric, operator, and threshold");
            }
            if (assertion.metricScope() == MetricScope.ALL && !GLOBAL_METRICS.contains(metric)) {
                throw invalid("The selected metric is not a global metric");
            }
            if (!"MCAP".equals(dataType) && !GENERIC_METRICS.contains(metric)) {
                throw invalid("The selected metric is not available for this data type");
            }
            if (assertion.metricScope() != MetricScope.ALL && !TOPIC_METRICS.contains(metric)) {
                throw invalid("The selected metric is not a topic metric");
            }
            if (assertion.metricScope() != MetricScope.ALL
                    && !StringUtils.hasText(assertion.matchPattern())) {
                throw invalid("Topic and schema assertions require a match pattern");
            }
        } else if (!StringUtils.hasText(assertion.matchPattern())) {
            throw invalid("Topic assertions require a match pattern");
        }
    }

    private void requireRuleManagement(RuleRow rule, long userId, boolean admin) {
        if (admin) {
            return;
        }
        if ("GLOBAL".equals(rule.getScope())
                || rule.getProjectId() == null
                || mapper.countManagedProject(rule.getProjectId(), userId) == 0) {
            throw forbidden("Quality rule cannot be modified by the current user");
        }
    }

    private List<AssertionResult> evaluate(
            List<AssertionRequest> assertions,
            ReportRequest report
    ) {
        List<AssertionResult> results = new ArrayList<>();
        for (AssertionRequest assertion : assertions) {
            results.add(evaluate(assertion, report));
        }
        return results;
    }

    private AssertionResult evaluate(AssertionRequest assertion, ReportRequest report) {
        if (assertion.type() == AssertionType.REQUIRED_TOPIC) {
            boolean found = report.topics().stream()
                    .anyMatch(topic -> glob(assertion.matchPattern(), topic.topic()));
            return result(assertion, found, found ? assertion.matchPattern() : null,
                    found ? "Required topic is present" : "Required topic is missing");
        }
        if (assertion.type() == AssertionType.FORBIDDEN_TOPIC) {
            boolean found = report.topics().stream()
                    .anyMatch(topic -> glob(assertion.matchPattern(), topic.topic()));
            return result(assertion, !found, found ? assertion.matchPattern() : null,
                    found ? "Forbidden topic is present" : "Forbidden topic is absent");
        }
        if (assertion.metricScope() == MetricScope.ALL) {
            BigDecimal actual = report.globalMetrics().get(assertion.metric());
            boolean passed = actual != null && compare(actual, assertion.operator(), assertion.threshold());
            return result(assertion, passed, actual == null ? null : actual.toPlainString(),
                    actual == null ? "Metric is missing" : "Global metric evaluated");
        }
        List<TopicMetricReport> matches = report.topics().stream()
                .filter(topic -> assertion.metricScope() == MetricScope.TOPIC
                        ? glob(assertion.matchPattern(), topic.topic())
                        : glob(normalizeSchema(assertion.matchPattern()), normalizeSchema(topic.schema())))
                .toList();
        if (matches.isEmpty()) {
            return result(assertion, false, null, "No topic matched the configured scope");
        }
        List<BigDecimal> values = matches.stream()
                .map(topic -> topic.metrics().get(assertion.metric()))
                .toList();
        boolean passed = values.stream().allMatch(value ->
                value != null && compare(value, assertion.operator(), assertion.threshold())
        );
        String actual = values.stream()
                .map(value -> value == null ? "null" : value.toPlainString())
                .toList()
                .toString();
        return result(assertion, passed, actual,
                passed ? "All matching topic metrics passed" : "A matching topic metric failed");
    }

    private AssertionResult result(
            AssertionRequest assertion,
            boolean passed,
            String actual,
            String message
    ) {
        return new AssertionResult(assertion, passed, actual, message);
    }

    private boolean compare(BigDecimal actual, String operator, BigDecimal threshold) {
        int comparison = actual.compareTo(threshold);
        return switch (operator) {
            case "<=" -> comparison <= 0;
            case ">=" -> comparison >= 0;
            case "<" -> comparison < 0;
            case ">" -> comparison > 0;
            case "==" -> comparison == 0;
            case "!=" -> comparison != 0;
            default -> false;
        };
    }

    private boolean glob(String glob, String value) {
        StringBuilder regex = new StringBuilder("^");
        for (char character : glob.toCharArray()) {
            if (character == '*') {
                regex.append(".*");
            } else if (character == '?') {
                regex.append('.');
            } else {
                regex.append(Pattern.quote(String.valueOf(character)));
            }
        }
        return value != null && value.matches(regex.append('$').toString());
    }

    private String normalizeSchema(String value) {
        return value.toLowerCase(Locale.ROOT).replace("/msg/", "/");
    }

    private void apply(QualityRule rule, RuleRequest request) {
        rule.setName(request.name().trim());
        rule.setDescription(normalize(request.description()));
        rule.setScope(request.scope().name());
        rule.setProjectId(request.scope() == RuleScope.PROJECT ? request.projectId() : null);
        rule.setDataType(upper(request.dataType()));
        rule.setDatasetPattern(request.datasetPattern().trim());
        rule.setAlgorithmCode("MCAP".equals(rule.getDataType())
                ? "MCAP_STRUCTURAL"
                : "DATASET_INTEGRITY");
        rule.setEnabled(request.enabled());
        rule.setPriority(request.priority());
        rule.setAssertionsJson(write(request.assertions()));
    }

    private RuleView ruleView(RuleRow row, long userId, boolean admin) {
        boolean mutable = admin || "PROJECT".equals(row.getScope())
                && row.getProjectId() != null
                && mapper.countManagedProject(row.getProjectId(), userId) > 0;
        return new RuleView(
                row.getId(),
                row.getName(),
                row.getDescription(),
                row.getScope(),
                row.getProjectId(),
                row.getProjectName(),
                row.getDataType(),
                row.getDatasetPattern(),
                row.getAlgorithmCode(),
                Boolean.TRUE.equals(row.getEnabled()),
                row.getPriority(),
                assertions(row),
                row.getCreatorId(),
                row.getCreatorName(),
                mutable,
                mutable,
                row.getCreatedAt(),
                row.getUpdatedAt()
        );
    }

    private ExecutionView executionView(ExecutionRow row) {
        return new ExecutionView(
                row.getId(),
                row.getRuleId(),
                row.getRuleName(),
                row.getRuleScope(),
                row.getDatasetId(),
                row.getDatasetName(),
                row.getProjectId(),
                row.getProjectName(),
                row.getDataType(),
                row.getStatus(),
                row.getProgress(),
                row.getPassed(),
                row.getEffectivePass(),
                readResults(row.getResultJson()),
                row.getErrorMessage(),
                row.getTriggerType(),
                row.getOverridePass(),
                row.getOverrideReason(),
                row.getOverrideByName(),
                row.getOverrideAt(),
                row.getStartedAt(),
                row.getCompletedAt(),
                row.getCreatedAt()
        );
    }

    private RuleRow requireRule(long id) {
        RuleRow row = mapper.selectRule(id);
        if (row == null) {
            throw notFound("Quality rule not found");
        }
        return row;
    }

    private ExecutionRow requireExecution(long id, long userId, boolean admin) {
        ExecutionRow row = mapper.selectExecution(id, userId, admin);
        if (row == null) {
            throw notFound("Quality execution not found");
        }
        return row;
    }

    private List<AssertionRequest> assertions(RuleRow row) {
        try {
            return objectMapper.readValue(
                    row.getAssertionsJson(),
                    new TypeReference<List<AssertionRequest>>() {
                    }
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Quality rule assertions are invalid", exception);
        }
    }

    private ReportRequest readReport(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, ReportRequest.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Quality report is invalid", exception);
        }
    }

    private List<AssertionResult> readResults(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(
                    json,
                    new TypeReference<List<AssertionResult>>() {
                    }
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Quality result is invalid", exception);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw invalid("Quality configuration cannot be serialized");
        }
    }

    private long value(Long value) {
        return value == null ? 0 : value;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String upper(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_ARGUMENT, message);
    }

    private BusinessException conflict(String message) {
        return new BusinessException(ErrorCode.CONFLICT, message);
    }

    private BusinessException forbidden(String message) {
        return new BusinessException(ErrorCode.FORBIDDEN, message);
    }

    private BusinessException notFound(String message) {
        return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, message);
    }
}

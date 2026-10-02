package com.dayan.platform.service.impl;

import com.dayan.platform.common.api.ErrorCode;
import com.dayan.platform.common.exception.BusinessException;
import com.dayan.platform.dto.AuditLogQuery;
import com.dayan.platform.model.OperationLog;
import com.dayan.platform.repository.mapper.OperationLogMapper;
import com.dayan.platform.service.AuditLogService;
import com.dayan.platform.vo.AuditViews.OperationLogDetail;
import com.dayan.platform.vo.AuditViews.OperationLogSummary;
import com.dayan.platform.vo.PageResponse;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AuditLogServiceImpl implements AuditLogService {

    private final OperationLogMapper operationLogMapper;

    public AuditLogServiceImpl(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OperationLogSummary> page(AuditLogQuery query) {
        validateRange(query);
        String user = normalize(query.getUser(), false);
        String module = normalize(query.getModule(), true);
        String result = normalize(query.getResult(), true);
        long total = operationLogMapper.countFiltered(
                query.getUserId(),
                user,
                module,
                result,
                query.getStartTime(),
                query.getEndTime()
        );
        long offset = (long) (query.getPage() - 1) * query.getSize();
        var items = operationLogMapper.selectPage(
                query.getUserId(),
                user,
                module,
                result,
                query.getStartTime(),
                query.getEndTime(),
                query.getSize(),
                offset
        ).stream().map(this::summary).toList();
        return PageResponse.of(query.getPage(), query.getSize(), total, items);
    }

    @Override
    @Transactional(readOnly = true)
    public OperationLogDetail detail(long id) {
        OperationLog log = operationLogMapper.selectById(id);
        if (log == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Operation log not found");
        }
        return new OperationLogDetail(
                log.getId(),
                log.getOperatorId(),
                log.getOperatorName(),
                log.getModule(),
                log.getAction(),
                log.getTargetType(),
                log.getTargetId(),
                log.getResult(),
                log.getErrorSummary(),
                log.getIpAddress(),
                log.getUserAgent(),
                log.getRequestId(),
                log.getDetails(),
                log.getOccurredAt(),
                log.getDurationMs()
        );
    }

    private OperationLogSummary summary(OperationLog log) {
        return new OperationLogSummary(
                log.getId(),
                log.getOperatorId(),
                log.getOperatorName(),
                log.getModule(),
                log.getAction(),
                log.getTargetType(),
                log.getTargetId(),
                log.getResult(),
                log.getErrorSummary(),
                log.getRequestId(),
                log.getOccurredAt(),
                log.getDurationMs()
        );
    }

    private void validateRange(AuditLogQuery query) {
        if (query.getStartTime() != null
                && query.getEndTime() != null
                && query.getStartTime().isAfter(query.getEndTime())) {
            throw new BusinessException(
                    ErrorCode.INVALID_ARGUMENT,
                    "startTime must not be after endTime"
            );
        }
    }

    private String normalize(String value, boolean upperCase) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return upperCase ? normalized.toUpperCase(Locale.ROOT) : normalized;
    }
}

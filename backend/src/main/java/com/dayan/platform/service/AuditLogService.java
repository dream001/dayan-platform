package com.dayan.platform.service;

import com.dayan.platform.dto.AuditLogQuery;
import com.dayan.platform.vo.AuditViews.OperationLogDetail;
import com.dayan.platform.vo.AuditViews.OperationLogSummary;
import com.dayan.platform.vo.PageResponse;

public interface AuditLogService {

    PageResponse<OperationLogSummary> page(AuditLogQuery query);

    OperationLogDetail detail(long id);
}

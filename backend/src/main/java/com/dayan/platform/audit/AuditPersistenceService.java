package com.dayan.platform.audit;

import com.dayan.platform.model.OperationLog;
import com.dayan.platform.repository.mapper.OperationLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditPersistenceService {

    private final OperationLogMapper operationLogMapper;

    public AuditPersistenceService(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persist(OperationLog operationLog) {
        operationLogMapper.insert(operationLog);
    }
}

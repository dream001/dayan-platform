package com.dayan.platform.service.impl;

import com.dayan.platform.repository.mapper.MonitoringMapper;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccessLogPersistenceService {

    private final MonitoringMapper monitoringMapper;

    public AccessLogPersistenceService(MonitoringMapper monitoringMapper) {
        this.monitoringMapper = monitoringMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persist(
            Long userId,
            String username,
            String method,
            String requestPath,
            int statusCode,
            long durationMs,
            String ipAddress,
            String userAgent,
            String requestId,
            OffsetDateTime occurredAt
    ) {
        monitoringMapper.insertAccessLog(
                userId,
                username,
                method,
                requestPath,
                statusCode,
                durationMs,
                ipAddress,
                userAgent,
                requestId,
                occurredAt
        );
    }
}

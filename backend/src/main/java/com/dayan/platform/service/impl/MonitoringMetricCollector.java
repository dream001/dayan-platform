package com.dayan.platform.service.impl;

import com.dayan.platform.service.MonitoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MonitoringMetricCollector {

    private static final Logger log = LoggerFactory.getLogger(MonitoringMetricCollector.class);

    private final MonitoringService monitoringService;

    public MonitoringMetricCollector(MonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }

    @Scheduled(
            initialDelayString = "${app.monitor.initial-delay:5000}",
            fixedDelayString = "${app.monitor.collect-interval:60000}"
    )
    public void collect() {
        try {
            monitoringService.collect();
        } catch (RuntimeException exception) {
            log.warn("Unable to collect monitoring metrics", exception);
        }
    }
}

package com.cd.scheduler;

import com.cd.server.ProbeScheduleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class HostAutoProbeScheduler {

    private static final Logger log = LoggerFactory.getLogger(HostAutoProbeScheduler.class);

    private final ProbeScheduleService probeScheduleService;

    public HostAutoProbeScheduler(ProbeScheduleService probeScheduleService) {
        this.probeScheduleService = probeScheduleService;
    }

    @PostConstruct
    public void init() {
        log.info("Auto-probe scheduler initializing...");
        probeScheduleService.initScheduling();
        log.info("Auto-probe scheduler initialized");
    }
}
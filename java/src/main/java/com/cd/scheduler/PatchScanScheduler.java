package com.cd.scheduler;

import com.cd.server.PatchScanService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PatchScanScheduler {

    private static final Logger log = LoggerFactory.getLogger(PatchScanScheduler.class);
    private final PatchScanService patchScanService;

    public PatchScanScheduler(PatchScanService patchScanService) {
        this.patchScanService = patchScanService;
    }

    @PostConstruct
    public void init() {
        log.info("Patch scan scheduler initializing...");
        patchScanService.initScheduling();
        log.info("Patch scan scheduler initialized");
    }
}

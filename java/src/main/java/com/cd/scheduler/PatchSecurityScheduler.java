package com.cd.scheduler;

import com.cd.server.PatchSecurityRuleEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PatchSecurityScheduler {

    private static final Logger log = LoggerFactory.getLogger(PatchSecurityScheduler.class);

    private final PatchSecurityRuleEngine ruleEngine;

    public PatchSecurityScheduler(PatchSecurityRuleEngine ruleEngine) {
        this.ruleEngine = ruleEngine;
    }

    @Scheduled(cron = "0 0 2 * * ?")
    public void dailyFullReAnalysis() {
        log.info("[PATCH-SCHEDULER] Daily full re-analysis triggered");
        try {
            ruleEngine.analyzeAllHosts();
        } catch (Exception e) {
            log.error("[PATCH-SCHEDULER] Daily re-analysis failed: {}", e.getMessage(), e);
        }
    }
}
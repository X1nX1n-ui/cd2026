package com.cd.server.impl;

import com.cd.entity.Host;
import com.cd.entity.HostAssetProbeCommand;
import com.cd.entity.ProbeScheduleConfig;
import com.cd.mapper.ProbeScheduleConfigMapper;
import com.cd.server.HostProbeCommandService;
import com.cd.server.HostService;
import com.cd.server.ProbeScheduleService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Service
public class ProbeScheduleServiceImpl implements ProbeScheduleService {

    private static final Logger log = LoggerFactory.getLogger(ProbeScheduleServiceImpl.class);
    private static final String DEFAULT_CRON = "0 */5 * * * ?";
    private static final String TARGET_ALL_ONLINE = "all_online";
    private static final String TARGET_SELECTED = "selected";
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final ProbeScheduleConfigMapper configMapper;
    private final HostService hostService;
    private final JdbcTemplate jdbcTemplate;
    private final HostProbeCommandService hostProbeCommandService;
    private final TaskScheduler taskScheduler;

    private ScheduledFuture<?> scheduledFuture;
    private final ReentrantLock scheduleLock = new ReentrantLock();

    public ProbeScheduleServiceImpl(ProbeScheduleConfigMapper configMapper,
                                    JdbcTemplate jdbcTemplate,
                                    HostService hostService,
                                    HostProbeCommandService hostProbeCommandService,
                                    TaskScheduler taskScheduler) {
        this.configMapper = configMapper;
        this.hostService = hostService;
        this.jdbcTemplate = jdbcTemplate;
        this.hostProbeCommandService = hostProbeCommandService;
        this.taskScheduler = taskScheduler;
    }

    @Override
    public ProbeScheduleConfig getConfig() {
        ProbeScheduleConfig config = configMapper.selectCurrent();
        if (config == null) {
            config = createDefaultConfig();
        }
        return config;
    }

    @Override
    @Transactional
    public ProbeScheduleConfig saveConfig(ProbeScheduleConfig input) {
        ProbeScheduleConfig existing = configMapper.selectCurrent();
        if (existing == null) {
            ProbeScheduleConfig config = new ProbeScheduleConfig();
            config.setEnabled(input.getEnabled() != null ? input.getEnabled() : true);
            config.setCronExpression(blankToDefault(input.getCronExpression()));
            config.setTargetType(blankToDefault(input.getTargetType(), TARGET_ALL_ONLINE));
            config.setTargetHostIds(input.getTargetHostIds());
            config.setProbeAccount(input.getProbeAccount() != null ? input.getProbeAccount() : 1);
            config.setProbeService(input.getProbeService() != null ? input.getProbeService() : 1);
            config.setProbeProcess(input.getProbeProcess() != null ? input.getProbeProcess() : 1);
            config.setProbeApp(input.getProbeApp() != null ? input.getProbeApp() : 1);
            configMapper.insert(config);
            reschedule(config);
            return config;
        }

        if (input.getEnabled() != null) existing.setEnabled(input.getEnabled());
        if (!isBlank(input.getCronExpression())) existing.setCronExpression(input.getCronExpression().trim());
        if (!isBlank(input.getTargetType())) existing.setTargetType(input.getTargetType().trim());
        existing.setTargetHostIds(input.getTargetHostIds());
        if (input.getProbeAccount() != null) existing.setProbeAccount(input.getProbeAccount());
        if (input.getProbeService() != null) existing.setProbeService(input.getProbeService());
        if (input.getProbeProcess() != null) existing.setProbeProcess(input.getProbeProcess());
        if (input.getProbeApp() != null) existing.setProbeApp(input.getProbeApp());
        configMapper.update(existing);
        reschedule(existing);
        return existing;
    }

    @Override
    public void executeNow() {
        ProbeScheduleConfig config = getConfig();
        log.info("Manual trigger: executing probe now with config: enabled={}, targetType={}", config.getEnabled(), config.getTargetType());
        executeProbe(config);
    }

    public void initScheduling() {
        ensureTableExists();
        ProbeScheduleConfig config = getConfig();
        log.info("Initializing probe scheduling: enabled={}, cron={}, targetType={}",
                config.getEnabled(), config.getCronExpression(), config.getTargetType());
        reschedule(config);
    }

    private void ensureTableExists() {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS probe_schedule_config (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        enabled TINYINT(1) NOT NULL DEFAULT 1,
                        cron_expression VARCHAR(64) NOT NULL DEFAULT "0 */5 * * * ?",
                        target_type VARCHAR(32) NOT NULL DEFAULT "all_online",
                        target_host_ids TEXT NULL,
                        probe_account TINYINT(1) NOT NULL DEFAULT 1,
                        probe_service TINYINT(1) NOT NULL DEFAULT 1,
                        probe_process TINYINT(1) NOT NULL DEFAULT 1,
                        probe_app TINYINT(1) NOT NULL DEFAULT 1,
                        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            log.info("probe_schedule_config table ensured");
        } catch (Exception e) {
            log.error("Failed to ensure probe_schedule_config table: {}", e.getMessage());
        }
    }

    private void reschedule(ProbeScheduleConfig config) {
        scheduleLock.lock();
        try {
            if (scheduledFuture != null) {
                scheduledFuture.cancel(false);
                scheduledFuture = null;
            }

            if (config.getEnabled() == null || !config.getEnabled()) {
                log.info("Probe scheduling disabled");
                return;
            }

            String cron = blankToDefault(config.getCronExpression());
            CronTrigger trigger = new CronTrigger(cron, TimeZone.getTimeZone(ZONE));
            scheduledFuture = taskScheduler.schedule(() -> executeProbe(config), trigger);
            log.info("Probe scheduled with cron: {}", cron);
        } catch (Exception e) {
            log.error("Failed to schedule probe task: {}", e.getMessage());
        } finally {
            scheduleLock.unlock();
        }
    }

    private void executeProbe(ProbeScheduleConfig config) {
        try {
            List<Host> targets = resolveTargetHosts(config);
            if (targets.isEmpty()) {
                log.info("Probe: no target hosts found, skipping");
                return;
            }

            log.info("Probe starting for {} host(s)", targets.size());
            int success = 0, fail = 0;
            for (Host host : targets) {
                try {
                    HostAssetProbeCommand cmd = new HostAssetProbeCommand();
                    cmd.setHostName(host.getHostname());
                    cmd.setMacAddress(host.getMacAddress());
                    cmd.setAccount(nvl(config.getProbeAccount()));
                    cmd.setService(nvl(config.getProbeService()));
                    cmd.setProcess(nvl(config.getProbeProcess()));
                    cmd.setApp(nvl(config.getProbeApp()));
                    hostProbeCommandService.sendAssetProbeCommand(cmd);
                    success++;
                } catch (Exception e) {
                    fail++;
                    log.warn("Probe failed for host {}: {}", host.getHostname(), e.getMessage());
                }
            }
            log.info("Probe completed. Success: {}, Failed: {}", success, fail);
        } catch (Exception e) {
            log.error("Probe execution error: {}", e.getMessage(), e);
        }
    }

    private List<Host> resolveTargetHosts(ProbeScheduleConfig config) {
        String targetType = config.getTargetType();
        if (TARGET_SELECTED.equals(targetType) && !isBlank(config.getTargetHostIds())) {
            List<Long> ids = Arrays.stream(config.getTargetHostIds().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Long::parseLong)
                    .collect(Collectors.toList());
            if (ids.isEmpty()) return Collections.emptyList();
            return hostService.listOnlineHosts().stream()
                    .filter(h -> ids.contains(h.getId()))
                    .collect(Collectors.toList());
        }
        // all_online or default
        return hostService.listOnlineHosts();
    }

    private ProbeScheduleConfig createDefaultConfig() {
        ProbeScheduleConfig config = new ProbeScheduleConfig();
        config.setEnabled(true);
        config.setCronExpression(DEFAULT_CRON);
        config.setTargetType(TARGET_ALL_ONLINE);
        config.setTargetHostIds(null);
        config.setProbeAccount(1);
        config.setProbeService(1);
        config.setProbeProcess(1);
        config.setProbeApp(1);
        configMapper.insert(config);
        return config;
    }

    private static String blankToDefault(String value) {
        return blankToDefault(value, DEFAULT_CRON);
    }

    private static String blankToDefault(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static int nvl(Integer value) {
        return value != null && value == 1 ? 1 : 0;
    }
}
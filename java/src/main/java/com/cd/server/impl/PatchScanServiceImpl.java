package com.cd.server.impl;

import com.alibaba.fastjson.JSON;
import com.cd.entity.Host;
import com.cd.entity.InstalledPatch;
import com.cd.entity.PatchScanStrategy;
import com.cd.mapper.HostMapper;
import com.cd.mapper.InstalledPatchMapper;
import com.cd.mapper.PatchScanStrategyMapper;
import com.cd.server.HostService;
import org.springframework.jdbc.core.JdbcTemplate;
import com.cd.server.PatchScanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PatchScanServiceImpl implements PatchScanService {

    private static final Logger log = LoggerFactory.getLogger(PatchScanServiceImpl.class);
    private static final String AGENT_EXCHANGE = "agent_exchange";
    private static final String DEFAULT_CRON = "0 0 */6 * * ?";
    private static final String TARGET_ALL_ONLINE = "all_online";
    private static final String TARGET_SELECTED = "selected";
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final PatchScanStrategyMapper strategyMapper;
    private final InstalledPatchMapper installedPatchMapper;
    private final HostMapper hostMapper;
    private final HostService hostService;
    private final AmqpAdmin amqpAdmin;
    private final RabbitTemplate rabbitTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final TaskScheduler taskScheduler;

        private final ConcurrentHashMap<String, ScanTask> scanTaskMap = new ConcurrentHashMap<>();
private ScheduledFuture<?> scheduledFuture;
    private final ReentrantLock scheduleLock = new ReentrantLock();

    public PatchScanServiceImpl(JdbcTemplate jdbcTemplate,
                                 PatchScanStrategyMapper strategyMapper,
                                 InstalledPatchMapper installedPatchMapper,
                                 HostMapper hostMapper,
                                 HostService hostService,
                                 AmqpAdmin amqpAdmin,
                                 RabbitTemplate rabbitTemplate,
                                 TaskScheduler taskScheduler) {
        this.jdbcTemplate = jdbcTemplate;
        this.strategyMapper = strategyMapper;
        this.installedPatchMapper = installedPatchMapper;
        this.hostMapper = hostMapper;
        this.hostService = hostService;
        this.amqpAdmin = amqpAdmin;
        this.rabbitTemplate = rabbitTemplate;
        this.taskScheduler = taskScheduler;
    }

    @Override
    public PatchScanStrategy getConfig() {
        PatchScanStrategy config = strategyMapper.selectConfig();
        if (config == null) {
            config = createDefaultConfig();
        }
        return config;
    }

    @Override
    @Transactional
    public PatchScanStrategy saveConfig(PatchScanStrategy input) {
        PatchScanStrategy existing = strategyMapper.selectConfig();
        if (existing == null) {
            PatchScanStrategy config = new PatchScanStrategy();
            config.setEnabled(input.getEnabled() != null ? input.getEnabled() : 1);
            config.setCronExpression(blankToDefault(input.getCronExpression()));
            config.setTargetType(blankToDefault(input.getTargetType(), TARGET_ALL_ONLINE));
            config.setTargetHostIds(input.getTargetHostIds());
            config.setTimeoutMinutes(input.getTimeoutMinutes() != null ? input.getTimeoutMinutes() : 10);
            config.setConcurrency(input.getConcurrency() != null ? input.getConcurrency() : 20);
            config.setRetryCount(input.getRetryCount() != null ? input.getRetryCount() : 3);
            config.setAiAnalysisEnabled(input.getAiAnalysisEnabled() != null ? input.getAiAnalysisEnabled() : 1);
            config.setId(1L);
            strategyMapper.upsert(config);
            reschedule(config);
            return config;
        }

        if (input.getEnabled() != null) existing.setEnabled(input.getEnabled());
        if (!isBlank(input.getCronExpression())) existing.setCronExpression(input.getCronExpression().trim());
        if (!isBlank(input.getTargetType())) existing.setTargetType(input.getTargetType().trim());
        existing.setTargetHostIds(input.getTargetHostIds());
        if (input.getTimeoutMinutes() != null) existing.setTimeoutMinutes(input.getTimeoutMinutes());
        if (input.getConcurrency() != null) existing.setConcurrency(input.getConcurrency());
        if (input.getRetryCount() != null) existing.setRetryCount(input.getRetryCount());
        if (input.getAiAnalysisEnabled() != null) existing.setAiAnalysisEnabled(input.getAiAnalysisEnabled());
        strategyMapper.upsert(existing);
        reschedule(existing);
        return existing;
    }

    @Override
    public String executeNow() {
        PatchScanStrategy config = getConfig();
        log.info("[PATCH-SCAN] ========================================");
        log.info("[PATCH-SCAN] Manual trigger: user clicked execute now");
        log.info("[PATCH-SCAN] Config: enabled={}, cron={}, targetType={}", config.getEnabled(), config.getCronExpression(), config.getTargetType());
        log.info("[PATCH-SCAN] ========================================");
        String taskId = createScanTask();
        updateScanTask(taskId, "DISPATCHED", "命令已下发", 10);
        executeScan(config, taskId);
        return taskId;
    }

    public void initScheduling() {
        ensureTableExists();
        PatchScanStrategy config = getConfig();
        log.info("Initializing patch scan scheduling: enabled={}, cron={}", config.getEnabled(), config.getCronExpression());
        reschedule(config);
    }

    @Override
    public void executeScheduledScan() {
        PatchScanStrategy config = getConfig();
        if (config.getEnabled() == null || config.getEnabled() != 1) {
            return;
        }
        log.info("[PATCH-SCAN] ========================================");
        log.info("[PATCH-SCAN] Scheduled trigger: cron={}", config.getCronExpression());
        log.info("[PATCH-SCAN] ========================================");
        executeScan(config, null);
    }

    @Transactional
    public void savePatchResults(String macAddress, List<InstalledPatch> patches) {
        if (patches == null || patches.isEmpty()) return;
        installedPatchMapper.deleteByMacAddress(macAddress);
        installedPatchMapper.insertBatch(patches);
        log.info("Saved {} patches for mac={}", patches.size(), macAddress);
    }

    private void reschedule(PatchScanStrategy config) {
        scheduleLock.lock();
        try {
            if (scheduledFuture != null) {
                scheduledFuture.cancel(false);
                scheduledFuture = null;
            }
            if (config.getEnabled() == null || config.getEnabled() != 1) {
                log.info("Patch scan scheduling disabled");
                return;
            }
            String cron = blankToDefault(config.getCronExpression());
            CronTrigger trigger = new CronTrigger(cron, java.util.TimeZone.getTimeZone(ZONE));
            scheduledFuture = taskScheduler.schedule(() -> executeScan(config, null), trigger);
            log.info("Patch scan scheduled with cron: {}", cron);
        } catch (Exception e) {
            log.error("Failed to schedule patch scan: {}", e.getMessage());
        } finally {
            scheduleLock.unlock();
        }
    }

    private void executeScan(PatchScanStrategy config, String taskId) {
        try {
            List<Host> targets = resolveTargetHosts(config);
            if (targets.isEmpty()) {
                log.info("Patch scan: no target hosts found");
                return;
            }

            log.info("[PATCH-SCAN] ========== Starting patch scan execution ==========");
            log.info("[PATCH-SCAN] Target host count: {}", targets.size());

            // Declare the agent exchange and ensure per-host queues + bindings exist
            DirectExchange exchange = new DirectExchange(AGENT_EXCHANGE, true, false);
            amqpAdmin.declareExchange(exchange);
            log.info("[PATCH-SCAN] Exchange declared: exchange={}, durable=true", AGENT_EXCHANGE);

            int sent = 0;
            for (Host host : targets) {
                String mac = host.getMacAddress();
                if (mac == null || mac.isBlank()) continue;

                // Ensure agent queue and binding exist so messages aren't dropped
                String queueName = "agent_" + mac + "_queue";
                log.info("[PATCH-SCAN] Ensuring queue: queue={}", queueName);
                if (amqpAdmin.getQueueProperties(queueName) == null) {
                    amqpAdmin.declareQueue(new Queue(queueName, true));
                    log.info("[PATCH-SCAN] Queue declared: queue={}", queueName);
                } else {
                    log.info("[PATCH-SCAN] Queue already exists: queue={}", queueName);
                }
                Binding binding = BindingBuilder
                    .bind(new Queue(queueName, true))
                    .to(exchange)
                    .with(mac);
                amqpAdmin.declareBinding(binding);
                log.info("[PATCH-SCAN] Binding declared: exchange={}, queue={}, routingKey={}", AGENT_EXCHANGE, queueName, mac);

                Map<String, Object> cmd = new LinkedHashMap<>();
                cmd.put("type", "hotfix");
                cmd.put("hotfix", 1);
                cmd.put("macAddress", mac);
                String cmdJson = JSON.toJSONString(cmd);
                log.info("[PATCH-SCAN] Sending hotfix command: exchange={}, routingKey={}, payload={}", AGENT_EXCHANGE, mac, cmdJson);
                rabbitTemplate.convertAndSend(AGENT_EXCHANGE, mac, cmdJson);
                log.info("[PATCH-SCAN] Command sent successfully to mac={}", mac);
                sent++;
            }
            log.info("[PATCH-SCAN] ========== Scan complete: {} commands sent ==========", sent);
            if (taskId != null) updateScanTask(taskId, "DISPATCHED", "已向 " + sent + " 台主机下发扫描指令", 20);

            // Record last execution time
            try {
                PatchScanStrategy current = strategyMapper.selectConfig();
                if (current != null) {
                    current.setLastExecutedAt(java.time.LocalDateTime.now());
                    strategyMapper.upsert(current);
                }
            } catch (Exception ignored) {}
        } catch (Exception e) {
            log.error("Patch scan execution error: {}", e.getMessage(), e);
        }
    }

    private List<Host> resolveTargetHosts(PatchScanStrategy config) {
        if (TARGET_SELECTED.equals(config.getTargetType()) && !isBlank(config.getTargetHostIds())) {
            List<Long> ids = Arrays.stream(config.getTargetHostIds().split(","))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .map(Long::parseLong).toList();
            if (ids.isEmpty()) return Collections.emptyList();
            return hostService.listOnlineHosts().stream()
                    .filter(h -> ids.contains(h.getId())).toList();
        }
        return hostService.listOnlineHosts();
    }

    private void ensureTableExists() {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS `patch_scan_strategy` (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        enabled TINYINT(1) NOT NULL DEFAULT 1,
                        cron_expression VARCHAR(64) NOT NULL DEFAULT '0 0 */6 * * ?',
                        target_type VARCHAR(32) NOT NULL DEFAULT 'all_online',
                        target_host_ids TEXT NULL,
                        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);

            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS `installed_patch` (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        mac_address VARCHAR(64) NOT NULL,
                        patch_id VARCHAR(128) NOT NULL,
                        patch_type VARCHAR(64) NULL,
                        product_name VARCHAR(255) NULL,
                        product_version VARCHAR(128) NULL,
                        install_time DATETIME NULL,
                        install_status VARCHAR(32) NULL,
                        source VARCHAR(64) NULL,
                        signature_status VARCHAR(32) NULL,
                        reboot_required TINYINT(1) NULL,
                        superseded_by VARCHAR(128) NULL,
                        is_security_patch TINYINT(1) NULL,
                        raw_data TEXT NULL,
                        scan_time DATETIME NOT NULL,
                        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        KEY idx_installed_patch_mac_address (mac_address),
                        KEY idx_installed_patch_patch_id (patch_id),
                        KEY idx_installed_patch_scan_time (scan_time)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            log.info("patch_scan_strategy and installed_patch tables ensured");
        } catch (Exception e) {
            log.error("Failed to ensure patch tables: {}", e.getMessage());
        }
    }

    private PatchScanStrategy createDefaultConfig() {
        PatchScanStrategy config = new PatchScanStrategy();
        config.setId(1L);
        config.setEnabled(1);
        config.setCronExpression(DEFAULT_CRON);
        config.setTargetType(TARGET_ALL_ONLINE);
        strategyMapper.upsert(config);
        return config;
    }

    private static String blankToDefault(String value) {
        return blankToDefault(value, DEFAULT_CRON);
    }

    private static String blankToDefault(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value.trim();
    }

    @Override
    public java.util.Map<String, Object> getStatus() {
        PatchScanStrategy config = getConfig();
        java.util.Map<String, Object> status = new java.util.LinkedHashMap<>();
        status.put("enabled", config.getEnabled() == 1);
        status.put("cronExpression", config.getCronExpression());
        status.put("targetType", config.getTargetType());
        status.put("targetHostIds", config.getTargetHostIds());
        status.put("timeoutMinutes", config.getTimeoutMinutes() != null ? config.getTimeoutMinutes() : 10);
        status.put("concurrency", config.getConcurrency() != null ? config.getConcurrency() : 20);
        status.put("retryCount", config.getRetryCount() != null ? config.getRetryCount() : 3);
        status.put("aiAnalysisEnabled", config.getAiAnalysisEnabled() != null ? config.getAiAnalysisEnabled() == 1 : true);
        status.put("lastExecutedAt", config.getLastExecutedAt());
        status.put("nextExecutionAt", config.getNextExecutionAt());

        // Estimate host count
        try {
            List<Host> targets = resolveTargetHosts(config);
            status.put("estimatedHostCount", targets.size());
        } catch (Exception e) {
            status.put("estimatedHostCount", 0);
        }
        return status;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    // ========== Scan task tracking ==========
    public static class ScanTask {
        public String taskId;
        public String status; // PENDING, DISPATCHED, PATCHES_RECEIVED, ANALYZING, AI_ANALYZING, COMPLETED, FAILED
        public String stageText;
        public int progress;
        public int patchCount;
        public int hostCount;
        public String errorMessage;
        public ScanTask(String taskId) { this.taskId = taskId; this.status = "PENDING"; this.stageText = "等待开始"; this.progress = 0; }
    }

    public String createScanTask() {
        String taskId = java.util.UUID.randomUUID().toString().substring(0, 8);
        ScanTask task = new ScanTask(taskId);
        scanTaskMap.put(taskId, task);
        return taskId;
    }

    public void updateScanTask(String taskId, String status, String stageText, int progress) {
        ScanTask task = scanTaskMap.get(taskId);
        if (task != null) {
            task.status = status;
            task.stageText = stageText;
            task.progress = progress;
        }
    }

    public ScanTask getScanTask(String taskId) {
        return scanTaskMap.get(taskId);
    }

    public void updateScanTaskResult(String taskId, int patchCount, int hostCount) {
        ScanTask task = scanTaskMap.get(taskId);
        if (task != null) {
            task.patchCount = patchCount;
            task.hostCount = hostCount;
            task.status = "COMPLETED";
            task.stageText = "分析完成";
            task.progress = 100;
        }
    }

    public void updateLatestTask(String status, String stageText, int progress, int patchCount) {
        // Find the most recent active task and update it
        ScanTask latest = null;
        for (ScanTask t : scanTaskMap.values()) {
            if (!"COMPLETED".equals(t.status) && !"FAILED".equals(t.status)) {
                if (latest == null || t.taskId.compareTo(latest.taskId) > 0) {
                    latest = t;
                }
            }
        }
        if (latest != null) {
            latest.status = status;
            latest.stageText = stageText;
            latest.progress = progress;
            latest.patchCount = patchCount;
        }
    }
}

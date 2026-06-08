package com.cd.mq;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.cd.entity.Host;
import com.cd.entity.InstalledPatch;
import com.cd.server.AssetDataService;
import com.cd.server.HostService;
import com.cd.server.PatchScanService;
import com.cd.server.impl.PatchScanServiceImpl;
import com.cd.server.PatchSecurityRuleEngine;
import com.cd.mapper.HostMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

@Component
public class RabbitMqHostListener {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqHostListener.class);

    private static final String SYSINFO_QUEUE = "sysinfo_queue";
    private static final String STATUS_QUEUE = "status_queue";
    private static final String SYSINFO_EXCHANGE = "sysinfo_exchange";
    private static final String AGENT_EXCHANGE = "agent_exchange";
    private static final String SYSINFO_ROUTING_KEY = "sysinfo";
    private static final String STATUS_ROUTING_KEY = "status";
    private static final String ACCOUNT_ROUTING_KEY = "account";
    private static final String SERVICE_ROUTING_KEY = "service";
    private static final String PROCESS_ROUTING_KEY = "process";
    private static final String APP_ROUTING_KEY = "app";
    private static final String ACCOUNT_QUEUE = "asset_account_queue";
    private static final String SERVICE_QUEUE = "asset_service_queue";
    private static final String PROCESS_QUEUE = "asset_process_queue";
    private static final String APP_QUEUE = "asset_app_queue";
    private static final String HOTFIX_QUEUE = "hotfix_queue";
    private static final String HOTFIX_ROUTING_KEY = "hotfix";

    private final HostService hostService;
    private final PatchScanService patchScanService;
    private final PatchSecurityRuleEngine patchRuleEngine;
    private final HostMapper hostMapper;
    private final AssetDataService assetDataService;
    private final PatchScanServiceImpl patchScanServiceImpl;
    private final AmqpAdmin amqpAdmin;

    public RabbitMqHostListener(HostService hostService, PatchScanService patchScanService, AssetDataService assetDataService, AmqpAdmin amqpAdmin, PatchSecurityRuleEngine patchRuleEngine, HostMapper hostMapper) {
        this.hostService = hostService;
        this.patchScanService = patchScanService;
        this.patchScanServiceImpl = (PatchScanServiceImpl) patchScanService;
        this.assetDataService = assetDataService;
        this.amqpAdmin = amqpAdmin;
        this.patchRuleEngine = patchRuleEngine;
        this.hostMapper = hostMapper;
    }

    @RabbitListener(
        bindings = @QueueBinding(
            value = @Queue(name = SYSINFO_QUEUE, durable = "true"),
            exchange = @Exchange(name = SYSINFO_EXCHANGE, type = ExchangeTypes.DIRECT, durable = "true"),
            key = SYSINFO_ROUTING_KEY
        )
    )
    public void onSysinfoMessage(String payload) {
        JSONObject jsonObject = JSON.parseObject(normalizePayload(payload));
        String macAddress = normalizeMacAddress(jsonObject.getString("mac_address"));

        ensureAgentResources(macAddress);

        Host host = new Host();
        host.setCpuArchitecture(jsonObject.getString("cpu_architecture"));
        host.setCpuName(jsonObject.getString("cpu_name"));
        host.setHostname(jsonObject.getString("hostname"));
        host.setIpAddress(jsonObject.getString("ip_address"));
        host.setLogicalCores(jsonObject.getInteger("logical_cores"));
        host.setMacAddress(macAddress);
        host.setMemoryAvailable(jsonObject.getString("memory_available"));
        host.setMemoryTotal(jsonObject.getString("memory_total"));
        host.setOsDetail(jsonObject.getString("os_detail"));
        host.setOsName(jsonObject.getString("os_name"));
        host.setOsType(jsonObject.getString("os_type"));
        host.setOsVersion(jsonObject.getString("os_version"));
        host.setOsBuild(jsonObject.getString("os_build"));
        host.setStatus("ONLINE");
        host.setLastSeenAt(LocalDateTime.now());

        hostService.saveOrUpdateFromQueue(host);
        log.info("Consumed sysinfo message for mac={}", host.getMacAddress());
    }

    @RabbitListener(
        bindings = @QueueBinding(
            value = @Queue(name = STATUS_QUEUE, durable = "true"),
            exchange = @Exchange(name = SYSINFO_EXCHANGE, type = ExchangeTypes.DIRECT, durable = "true"),
            key = STATUS_ROUTING_KEY
        )
    )
    public void onHeartbeatMessage(String payload) {
        JSONObject jsonObject = JSON.parseObject(normalizePayload(payload));
        String macAddress = normalizeMacAddress(jsonObject.getString("mac_address"));

        boolean updated = hostService.updateHeartbeatStatusByMacAddress(macAddress);
        if (updated) {
            log.info("Consumed heartbeat message and updated host status, mac={}", macAddress);
            return;
        }

        log.warn("Consumed heartbeat message but no host matched mac={}", macAddress);
    }

    @RabbitListener(
        bindings = @QueueBinding(
            value = @Queue(name = ACCOUNT_QUEUE, durable = "true"),
            exchange = @Exchange(name = SYSINFO_EXCHANGE, type = ExchangeTypes.DIRECT, durable = "true"),
            key = ACCOUNT_ROUTING_KEY
        )
    )
    public void onAccountAssetMessage(String payload) {
        handleAssetMessage(payload, ACCOUNT_ROUTING_KEY);
    }

    @RabbitListener(
        bindings = @QueueBinding(
            value = @Queue(name = SERVICE_QUEUE, durable = "true"),
            exchange = @Exchange(name = SYSINFO_EXCHANGE, type = ExchangeTypes.DIRECT, durable = "true"),
            key = SERVICE_ROUTING_KEY
        )
    )
    public void onServiceAssetMessage(String payload) {
        handleAssetMessage(payload, SERVICE_ROUTING_KEY);
    }

    @RabbitListener(
        bindings = @QueueBinding(
            value = @Queue(name = PROCESS_QUEUE, durable = "true"),
            exchange = @Exchange(name = SYSINFO_EXCHANGE, type = ExchangeTypes.DIRECT, durable = "true"),
            key = PROCESS_ROUTING_KEY
        )
    )
    public void onProcessAssetMessage(String payload) {
        handleAssetMessage(payload, PROCESS_ROUTING_KEY);
    }

    @RabbitListener(
        bindings = @QueueBinding(
            value = @Queue(name = HOTFIX_QUEUE, durable = "true"),
            exchange = @Exchange(name = SYSINFO_EXCHANGE, type = ExchangeTypes.DIRECT, durable = "true"),
            key = HOTFIX_ROUTING_KEY
        )
    )
    public void onHotfixMessage(String payload) {
        log.info("[HOTFIX-CONSUMER] ========== Received hotfix message from MQ ==========");
        JSONObject jsonObject = JSON.parseObject(normalizePayload(payload));
        String macAddress = normalizeMacAddress(jsonObject.getString("mac_address"));
        log.info("[HOTFIX-CONSUMER] Parsed MAC address: {}", macAddress);
        if (macAddress == null || macAddress.isBlank()) {
            log.warn("[HOTFIX-CONSUMER] Ignored hotfix message without mac_address");
            return;
        }

        List<InstalledPatch> patches = new ArrayList<>();
        com.alibaba.fastjson.JSONArray hotfixes = jsonObject.getJSONArray("hotfixes");
        if (hotfixes != null) {
            for (int i = 0; i < hotfixes.size(); i++) {
                JSONObject hp = hotfixes.getJSONObject(i);
                InstalledPatch patch = new InstalledPatch();
                patch.setMacAddress(macAddress);
                patch.setPatchId(hp.getString("patch_id"));
                patch.setPatchType(hp.getString("patch_type"));
                patch.setProductName(hp.getString("product_name"));
                patch.setProductVersion(hp.getString("product_version"));
                String installTime = hp.getString("install_time");
                if (installTime != null && !installTime.isEmpty()) {
                    try { patch.setInstallTime(LocalDateTime.parse(installTime)); } catch (Exception ignored) {}
                }
                patch.setInstallStatus(hp.getString("install_status"));
                patch.setSource(hp.getString("source"));
                patch.setSignatureStatus(hp.getString("signature_status"));
                patch.setRebootRequired(hp.getInteger("reboot_required"));
                patch.setSupersededBy(hp.getString("superseded_by"));
                patch.setIsSecurityPatch(hp.getInteger("is_security_patch"));
                patch.setRawData(hp.toJSONString());
                patch.setScanTime(LocalDateTime.now());
                patches.add(patch);
            }
        }
        patchScanService.savePatchResults(macAddress, patches);
        patchScanServiceImpl.updateLatestTask("PATCHES_RECEIVED", "补丁数据已入库: " + patches.size() + " 条", 50, patches.size());
        log.info("[HOTFIX-CONSUMER] Saved {} patches to DB for mac={}", patches.size(), macAddress);

        // Auto-trigger patch security analysis
        patchScanServiceImpl.updateLatestTask("ANALYZING", "正在执行安全规则分析...", 60, patches.size());
        try {
            Host host = hostMapper.selectByMacAddress(macAddress);
            if (host == null) {
                log.warn("[HOTFIX-CONSUMER] selectByMacAddress returned null for mac={}, trying fallback", macAddress);
                // Fallback: try online hosts
                var onlineHosts = hostMapper.selectOnlineHosts();
                for (var h : onlineHosts) {
                    String hmac = h.getMacAddress();
                    if (hmac != null && (hmac.equalsIgnoreCase(macAddress) || hmac.replace("-","").equalsIgnoreCase(macAddress.replace("-","")))) {
                        host = h;
                        log.info("[HOTFIX-CONSUMER] Found host via fallback: id={} hostname={}", host.getId(), host.getHostname());
                        break;
                    }
                }
            }
            if (host != null) {
                log.info("[HOTFIX-CONSUMER] Triggering auto patch analysis for host={}", host.getHostname());
                patchRuleEngine.analyzeAndPersist(host);
                patchScanServiceImpl.updateLatestTask("COMPLETED", "分析完成", 100, patches.size());
                log.info("[HOTFIX-CONSUMER] Auto-analysis completed for host={}", host.getHostname());
            } else {
                log.warn("[HOTFIX-CONSUMER] No host found for mac={}, skipping analysis", macAddress);
                patchScanServiceImpl.updateLatestTask("FAILED", "未找到对应主机: " + macAddress, 60, patches.size());
            }
        } catch (Exception e) {
            log.error("[HOTFIX-CONSUMER] Auto-analysis failed for mac={}: {}", macAddress, e.getMessage(), e);
            patchScanServiceImpl.updateLatestTask("FAILED", "分析失败: " + e.getMessage(), 60, patches.size());
        }

        log.info("[HOTFIX-CONSUMER] ========== Hotfix message processing complete ==========");
    }

    @RabbitListener(
        bindings = @QueueBinding(
            value = @Queue(name = APP_QUEUE, durable = "true"),
            exchange = @Exchange(name = SYSINFO_EXCHANGE, type = ExchangeTypes.DIRECT, durable = "true"),
            key = APP_ROUTING_KEY
        )
    )
    public void onAppAssetMessage(String payload) {
        handleAssetMessage(payload, APP_ROUTING_KEY);
    }

    private void ensureAgentResources(String macAddress) {
        if (macAddress == null || macAddress.isBlank()) {
            log.warn("Skip agent queue creation because mac_address is empty.");
            return;
        }

        String queueName = buildAgentQueueName(macAddress);
        if (amqpAdmin.getQueueProperties(queueName) == null) {
            amqpAdmin.declareQueue(new org.springframework.amqp.core.Queue(queueName, true));
            log.info("Declared agent queue: {}", queueName);
        }

        DirectExchange exchange = new DirectExchange(AGENT_EXCHANGE, true, false);
        amqpAdmin.declareExchange(exchange);

        Binding binding = BindingBuilder
            .bind(new org.springframework.amqp.core.Queue(queueName, true))
            .to(exchange)
            .with(macAddress);
        amqpAdmin.declareBinding(binding);

        log.info(
            "Ensured agent exchange binding. exchange={}, queue={}, routingKey={}",
            AGENT_EXCHANGE,
            queueName,
            macAddress
        );
    }

    private String buildAgentQueueName(String macAddress) {
        return "agent_" + macAddress + "_queue";
    }

    private String normalizeMacAddress(String macAddress) {
        if (macAddress == null) {
            return null;
        }
        return macAddress.trim().replace(':', '-').toUpperCase();
    }

    private String normalizePayload(String payload) {
        if (payload == null) {
            return "{}";
        }
        return payload;
    }

    private void handleAssetMessage(String payload, String assetType) {
        boolean saved = assetDataService.saveAssetData(assetType, payload);
        if (saved) {
            log.info("Consumed {} asset message and saved it successfully.", assetType);
            return;
        }

        log.warn("Ignored {} asset message because payload format is invalid. payload={}", assetType, payload);
    }
}

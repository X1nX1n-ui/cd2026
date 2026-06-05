package com.cd.mq;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.cd.entity.Host;
import com.cd.server.AssetDataService;
import com.cd.server.HostService;
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

    private final HostService hostService;
    private final AssetDataService assetDataService;
    private final AmqpAdmin amqpAdmin;

    public RabbitMqHostListener(HostService hostService, AssetDataService assetDataService, AmqpAdmin amqpAdmin) {
        this.hostService = hostService;
        this.assetDataService = assetDataService;
        this.amqpAdmin = amqpAdmin;
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
        host.setStatus("ONLINE");
        host.setLastSeenAt(LocalDateTime.now());
        host.setSourceQueue(SYSINFO_QUEUE);
        host.setRawPayload(payload);

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
        return payload.replace('\'', '"');
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

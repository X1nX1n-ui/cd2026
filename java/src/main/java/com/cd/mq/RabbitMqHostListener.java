package com.cd.mq;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.cd.entity.Host;
import com.cd.server.HostService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final String SYSINFO_ROUTING_KEY = "sysinfo";
    private static final String STATUS_ROUTING_KEY = "status";

    private final HostService hostService;

    public RabbitMqHostListener(HostService hostService) {
        this.hostService = hostService;
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

        Host host = new Host();
        host.setCpuArchitecture(jsonObject.getString("cpu_architecture"));
        host.setCpuName(jsonObject.getString("cpu_name"));
        host.setHostname(jsonObject.getString("hostname"));
        host.setIpAddress(jsonObject.getString("ip_address"));
        host.setLogicalCores(jsonObject.getInteger("logical_cores"));
        host.setMacAddress(jsonObject.getString("mac_address"));
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
        String macAddress = jsonObject.getString("mac_address");

        boolean updated = hostService.updateHeartbeatStatusByMacAddress(macAddress);
        if (updated) {
            log.info("Consumed heartbeat message and updated host status, mac={}", macAddress);
            return;
        }

        log.warn("Consumed heartbeat message but no host matched mac={}", macAddress);
    }

    private String normalizePayload(String payload) {
        if (payload == null) {
            return "{}";
        }
        return payload.replace('\'', '"');
    }
}

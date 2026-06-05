package com.cd.server.impl;

import com.alibaba.fastjson.JSON;
import com.cd.entity.Host;
import com.cd.entity.HostAssetProbeCommand;
import com.cd.exception.BusinessException;
import com.cd.mapper.HostMapper;
import com.cd.server.HostProbeCommandService;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class HostProbeCommandServiceImpl implements HostProbeCommandService {

    private static final String AGENT_EXCHANGE = "agent_exchange";
    private static final String ASSET_PROBE_TYPE = "assets";

    private final AmqpAdmin amqpAdmin;
    private final RabbitTemplate rabbitTemplate;
    private final HostMapper hostMapper;

    public HostProbeCommandServiceImpl(AmqpAdmin amqpAdmin, RabbitTemplate rabbitTemplate, HostMapper hostMapper) {
        this.amqpAdmin = amqpAdmin;
        this.rabbitTemplate = rabbitTemplate;
        this.hostMapper = hostMapper;
    }

    @Override
    public void sendAssetProbeCommand(HostAssetProbeCommand command) {
        HostAssetProbeCommand normalizedCommand = normalize(command);
        validate(normalizedCommand);
        validateHostOnline(normalizedCommand.getMacAddress());

        amqpAdmin.declareExchange(new DirectExchange(AGENT_EXCHANGE, true, false));
        String message = JSON.toJSONString(normalizedCommand);
        rabbitTemplate.convertAndSend(AGENT_EXCHANGE, normalizedCommand.getMacAddress(), message);
    }

    private HostAssetProbeCommand normalize(HostAssetProbeCommand command) {
        HostAssetProbeCommand normalized = new HostAssetProbeCommand();
        normalized.setAccount(toFlag(command == null ? null : command.getAccount()));
        normalized.setService(toFlag(command == null ? null : command.getService()));
        normalized.setProcess(toFlag(command == null ? null : command.getProcess()));
        normalized.setApp(toFlag(command == null ? null : command.getApp()));
        normalized.setHostName(command == null ? null : trimToNull(command.getHostName()));
        normalized.setMacAddress(normalizeMacAddress(command == null ? null : command.getMacAddress()));
        normalized.setType(ASSET_PROBE_TYPE);
        return normalized;
    }

    private void validate(HostAssetProbeCommand command) {
        if (command.getMacAddress() == null) {
            throw new BusinessException("MAC address cannot be empty");
        }
        if (command.getAccount() == 0
            && command.getService() == 0
            && command.getProcess() == 0
            && command.getApp() == 0) {
            throw new BusinessException("At least one probe option must be selected");
        }
    }

    private void validateHostOnline(String macAddress) {
        Host host = hostMapper.selectByMacAddress(macAddress);
        if (host == null) {
            throw new BusinessException("Host not found");
        }

        LocalDateTime updatedAt = host.getUpdatedAt();
        if (updatedAt == null || updatedAt.plusSeconds(4).isBefore(LocalDateTime.now())) {
            hostMapper.updateStatusAndUpdatedAtByMacAddress(macAddress, "0");
            throw new BusinessException("主机已下线");
        }

        hostMapper.updateStatusAndUpdatedAtByMacAddress(macAddress, "1");
    }

    private Integer toFlag(Integer value) {
        return value != null && value == 1 ? 1 : 0;
    }

    private String normalizeMacAddress(String macAddress) {
        if (macAddress == null) {
            return null;
        }
        String normalized = macAddress.trim().replace(':', '-').toUpperCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}

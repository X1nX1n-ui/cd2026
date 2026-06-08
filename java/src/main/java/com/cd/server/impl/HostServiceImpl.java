package com.cd.server.impl;

import com.cd.entity.Host;
import com.cd.entity.PageResult;
import com.cd.exception.BusinessException;
import com.cd.exception.ResourceNotFoundException;
import com.cd.mapper.HostMapper;
import com.cd.server.HostService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class HostServiceImpl implements HostService {

    private final HostMapper hostMapper;

    public HostServiceImpl(HostMapper hostMapper) {
        this.hostMapper = hostMapper;
    }

    @Override
    public PageResult<Host> page(int pageNo, int pageSize, String keyword, String status) {
        refreshHostStatuses();

        int validPageNo = Math.max(pageNo, 1);
        int validPageSize = Math.max(pageSize, 1);
        int offset = (validPageNo - 1) * validPageSize;

        String normalizedKeyword = trimToNull(keyword);
        String normalizedStatus = trimToNull(status);

        PageResult<Host> result = new PageResult<>();
        result.setPageNo(validPageNo);
        result.setPageSize(validPageSize);
        result.setTotal(hostMapper.count(normalizedKeyword, normalizedStatus));
        result.setRecords(hostMapper.selectPage(normalizedKeyword, normalizedStatus, offset, validPageSize));
        return result;
    }

    @Override
    public Host getById(Long id) {
        refreshHostStatuses();
        Host host = hostMapper.selectById(id);
        if (host == null) {
            throw new ResourceNotFoundException("host not found, id=" + id);
        }
        return host;
    }

    @Override
    @Transactional
    public Host create(Host host) {
        normalize(host, true);
        validate(host, true);
        hostMapper.insert(host);
        return getById(host.getId());
    }

    @Override
    @Transactional
    public Host update(Host host) {
        if (host.getId() == null) {
            throw new BusinessException("Host ID cannot be empty");
        }
        Host existing = hostMapper.selectById(host.getId());
        if (existing == null) {
            throw new ResourceNotFoundException("host not found, id=" + host.getId());
        }
        mergeForUpdate(existing, host);
        normalize(existing, false);
        validate(existing, false);
        hostMapper.update(existing);
        return getById(existing.getId());
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        Host existing = hostMapper.selectById(id);
        if (existing == null) {
            throw new ResourceNotFoundException("host not found, id=" + id);
        }
        hostMapper.deleteById(id);
    }

    @Override
    @Transactional
    public Host saveOrUpdateFromQueue(Host host) {
        normalize(host, false);
        validate(host, false);
        if (host.getStatus() == null) {
            host.setStatus("ONLINE");
        }
        if (host.getLastSeenAt() == null) {
            host.setLastSeenAt(LocalDateTime.now());
        }
        hostMapper.upsertByMacAddress(host);
        Host saved = hostMapper.selectByMacAddress(host.getMacAddress());
        if (saved == null) {
            throw new BusinessException("Host save failed");
        }
        return saved;
    }

    @Override
    @Transactional
    public boolean updateHeartbeatStatusByMacAddress(String macAddress) {
        String normalizedMacAddress = normalizeMac(macAddress);
        if (normalizedMacAddress == null) {
            throw new BusinessException("MAC address cannot be empty");
        }
        return hostMapper.updateStatusAndUpdatedAtByMacAddress(normalizedMacAddress, "1") > 0;
    }

    @Override
    public java.util.List<Host> listOnlineHosts() {
        refreshHostStatuses();
        return hostMapper.selectOnlineHosts();
    }

    private void refreshHostStatuses() {
        hostMapper.refreshStatusesByUpdatedAt();
    }

    private void mergeForUpdate(Host existing, Host incoming) {
        if (incoming.getHostname() != null) {
            existing.setHostname(incoming.getHostname());
        }
        if (incoming.getIpAddress() != null) {
            existing.setIpAddress(incoming.getIpAddress());
        }
        if (incoming.getMacAddress() != null) {
            existing.setMacAddress(incoming.getMacAddress());
        }
        if (incoming.getCpuArchitecture() != null) {
            existing.setCpuArchitecture(incoming.getCpuArchitecture());
        }
        if (incoming.getCpuName() != null) {
            existing.setCpuName(incoming.getCpuName());
        }
        if (incoming.getLogicalCores() != null) {
            existing.setLogicalCores(incoming.getLogicalCores());
        }
        if (incoming.getMemoryAvailable() != null) {
            existing.setMemoryAvailable(incoming.getMemoryAvailable());
        }
        if (incoming.getMemoryTotal() != null) {
            existing.setMemoryTotal(incoming.getMemoryTotal());
        }
        if (incoming.getOsDetail() != null) {
            existing.setOsDetail(incoming.getOsDetail());
        }
        if (incoming.getOsName() != null) {
            existing.setOsName(incoming.getOsName());
        }
        if (incoming.getOsType() != null) {
            existing.setOsType(incoming.getOsType());
        }
        if (incoming.getOsVersion() != null) {
            existing.setOsVersion(incoming.getOsVersion());
        }
        if (incoming.getOsBuild() != null) {
            existing.setOsBuild(incoming.getOsBuild());
        }
        if (incoming.getStatus() != null) {
            existing.setStatus(incoming.getStatus());
        }
        if (incoming.getLastSeenAt() != null) {
            existing.setLastSeenAt(incoming.getLastSeenAt());
        }
    }

    private void normalize(Host host, boolean createMode) {
        host.setHostname(trimToNull(host.getHostname()));
        host.setIpAddress(trimToNull(host.getIpAddress()));
        host.setMacAddress(normalizeMac(host.getMacAddress()));
        host.setCpuArchitecture(trimToNull(host.getCpuArchitecture()));
        host.setCpuName(trimToNull(host.getCpuName()));
        host.setMemoryAvailable(trimToNull(host.getMemoryAvailable()));
        host.setMemoryTotal(trimToNull(host.getMemoryTotal()));
        host.setOsDetail(trimToNull(host.getOsDetail()));
        host.setOsName(trimToNull(host.getOsName()));
        host.setOsType(trimToNull(host.getOsType()));
        host.setOsVersion(trimToNull(host.getOsVersion()));
        host.setOsBuild(trimToNull(host.getOsBuild()));
        host.setStatus(normalizeStatus(host.getStatus(), createMode));
    }

    private void validate(Host host, boolean createMode) {
        if (createMode && host.getHostname() == null) {
            throw new BusinessException("Host name cannot be empty");
        }
        if (host.getMacAddress() == null) {
            throw new BusinessException("MAC address cannot be empty");
        }
        Host duplicated = hostMapper.selectByMacAddress(host.getMacAddress());
        if (duplicated != null && (createMode || !duplicated.getId().equals(host.getId()))) {
            throw new BusinessException("MAC address already exists");
        }
    }

    private String normalizeStatus(String status, boolean createMode) {
        String value = trimToNull(status);
        if (value == null) {
            return createMode ? "UNKNOWN" : null;
        }
        return value.toUpperCase(Locale.ROOT);
    }

    private String normalizeMac(String macAddress) {
        String value = trimToNull(macAddress);
        if (value == null) {
            return null;
        }
        return value.replace(':', '-').toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
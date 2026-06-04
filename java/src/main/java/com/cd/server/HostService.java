package com.cd.server;

import com.cd.entity.Host;
import com.cd.entity.PageResult;

public interface HostService {

    PageResult<Host> page(int pageNo, int pageSize, String keyword, String status);

    Host getById(Long id);

    Host create(Host host);

    Host update(Host host);

    void deleteById(Long id);

    Host saveOrUpdateFromQueue(Host host);

    boolean updateHeartbeatStatusByMacAddress(String macAddress);
}

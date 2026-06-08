package com.cd.server;

import com.cd.entity.ProbeScheduleConfig;

public interface ProbeScheduleService {

    ProbeScheduleConfig getConfig();

    ProbeScheduleConfig saveConfig(ProbeScheduleConfig config);

    void executeNow();

    void initScheduling();
}

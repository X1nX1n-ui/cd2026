package com.cd.server;

import com.cd.entity.InstalledPatch;
import com.cd.entity.PatchScanStrategy;

import java.util.List;

public interface PatchScanService {

    PatchScanStrategy getConfig();

    PatchScanStrategy saveConfig(PatchScanStrategy config);

    String executeNow();

    void executeScheduledScan();

    java.util.Map<String, Object> getStatus();

    void initScheduling();

    void savePatchResults(String macAddress, List<InstalledPatch> patches);
}

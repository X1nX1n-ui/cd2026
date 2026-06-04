package com.cd.server;

import com.cd.entity.HostAssetProbeCommand;

public interface HostProbeCommandService {

    void sendAssetProbeCommand(HostAssetProbeCommand command);
}

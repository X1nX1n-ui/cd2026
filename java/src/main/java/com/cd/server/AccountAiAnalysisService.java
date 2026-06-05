package com.cd.server;

import com.cd.entity.AssetSnapshotView;
import com.cd.entity.Host;

public interface AccountAiAnalysisService {

    AssetSnapshotView analyzeLatestAccountSnapshot(Host host);
}

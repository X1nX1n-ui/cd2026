package com.cd.server;

import com.cd.entity.AssetSnapshotView;
import com.cd.entity.Host;

import java.util.Map;

public interface AssetAiAnalysisService {

    AssetSnapshotView analyzeLatestSnapshot(Host host, String assetType);

    Map<String, Object> startAnalyzeLatestSnapshotTask(Host host, String assetType);

    Map<String, Object> getAnalyzeTaskStatus(String taskId);

    Map<String, Object> normalizeStoredRiskLevels(String assetType);
}

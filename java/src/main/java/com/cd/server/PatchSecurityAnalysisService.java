package com.cd.server;

import com.cd.entity.PatchSecurityAnalysisResult;

import java.util.Map;

public interface PatchSecurityAnalysisService {

    PatchSecurityAnalysisResult analyzeHost(Long hostId);

    Map<String, Object> startAnalysisTask(Long hostId);

    Map<String, Object> getTaskStatus(String taskId);
}
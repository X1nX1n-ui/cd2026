package com.cd.server.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.cd.entity.Host;
import com.cd.entity.PatchAiSummary;
import com.cd.entity.PatchSecurityAnalysisResult;
import com.cd.exception.BusinessException;
import com.cd.mapper.HostMapper;
import com.cd.mapper.PatchSecurityResultMapper;
import com.cd.server.PatchSecurityAnalysisProvider;
import com.cd.server.PatchSecurityAnalysisService;
import com.cd.server.PatchSecurityRuleEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class PatchSecurityAnalysisServiceImpl implements PatchSecurityAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(PatchSecurityAnalysisServiceImpl.class);

    private static final String API_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    private static final String MODEL = "glm-5";
    private static final int TIMEOUT_SECONDS = 120;

    private final PatchSecurityRuleEngine ruleEngine;
    private final PatchSecurityAnalysisProvider provider;
    private final HostMapper hostMapper;
    private final PatchSecurityResultMapper resultMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final ConcurrentHashMap<String, TaskState> taskMap = new ConcurrentHashMap<>();

    public PatchSecurityAnalysisServiceImpl(PatchSecurityRuleEngine ruleEngine,
                                             PatchSecurityAnalysisProvider provider,
                                             HostMapper hostMapper,
                                             PatchSecurityResultMapper resultMapper) {
        this.ruleEngine = ruleEngine;
        this.provider = provider;
        this.hostMapper = hostMapper;
        this.resultMapper = resultMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PatchSecurityAnalysisResult analyzeHost(Long hostId) {
        Host host = hostMapper.selectById(hostId);
        if (host == null) throw new RuntimeException("Host not found: " + hostId);
        PatchSecurityAnalysisResult result = ruleEngine.analyzeAndPersist(host);

        // Call AI for summary (synchronous for simple API)
        try {
            PatchAiSummary aiSummary = callAi(result);
            result.setSummary(aiSummary.getSummary() != null ? aiSummary.getSummary() : buildFallbackSummary(result));
                resultMapper.updateAiSummary(hostId, result.getSummary());
        } catch (Exception e) {
            log.warn("AI summary failed for host={}, using fallback. Error: {}", hostId, e.getMessage());
            result.setSummary(buildFallbackSummary(result));
                resultMapper.updateAiSummary(hostId, result.getSummary());
        }

        return result;
    }

    @Override
    public Map<String, Object> startAnalysisTask(Long hostId) {
        String taskId = UUID.randomUUID().toString();
        TaskState state = new TaskState(taskId, hostId);
        taskMap.put(taskId, state);

        executor.submit(() -> {
            try {
                state.update("analyzing", "正在执行补丁安全规则分析...", 30);
                Host host = hostMapper.selectById(hostId);
                if (host == null) throw new RuntimeException("Host not found: " + hostId);
                PatchSecurityAnalysisResult result = ruleEngine.analyzeAndPersist(host);

                state.update("ai-summary", "正在生成AI安全分析报告...", 70);
                // Start a progress updater to show incremental progress during AI call
                java.util.concurrent.atomic.AtomicBoolean aiDone = new java.util.concurrent.atomic.AtomicBoolean(false);
                java.util.concurrent.ScheduledExecutorService progressScheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
                progressScheduler.scheduleAtFixedRate(() -> {
                    if (!aiDone.get() && !"FAILED".equals(state.status)) {
                        int p = state.progress;
                        if (p < 85) {
                            state.update("ai-summary", "AI正在分析补丁安全风险...", p + 3);
                        } else if (p < 90) {
                            state.update("ai-summary", "AI正在生成修复建议...", p + 1);
                        }
                    }
                }, 2, 2, java.util.concurrent.TimeUnit.SECONDS);
                try {
                    PatchAiSummary aiSummary = callAi(result);
                    result.setSummary(aiSummary.getSummary() != null ? aiSummary.getSummary() : buildFallbackSummary(result));
                    resultMapper.updateAiSummary(hostId, result.getSummary());
                } catch (Exception e) {
                    log.warn("AI summary failed, using fallback: {}", e.getMessage());
                    result.setSummary(buildFallbackSummary(result));
                    resultMapper.updateAiSummary(hostId, result.getSummary());
                } finally {
                    aiDone.set(true);
                    progressScheduler.shutdownNow();
                }

                state.setResult(result);
                state.setStatus("COMPLETED");
                state.update("completed", "分析完成", 100);
                log.info("Patch analysis task {} completed for host={}", taskId, hostId);
            } catch (Exception e) {
                log.error("Patch analysis task {} failed: {}", taskId, e.getMessage(), e);
                state.setStatus("FAILED");
                state.setErrorMessage(e.getMessage());
            }
        });

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("taskId", taskId);
        response.put("status", "PENDING");
        return response;
    }

    @Override
    public Map<String, Object> getTaskStatus(String taskId) {
        TaskState state = taskMap.get(taskId);
        if (state == null) {
            throw new BusinessException("任务不存在或已过期: " + taskId);
        }

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("taskId", state.taskId);
        status.put("status", state.status);
        status.put("stageKey", state.stageKey);
        status.put("stageText", state.stageText);
        status.put("progress", state.progress);
        if (state.errorMessage != null) {
            status.put("message", state.errorMessage);
        }
        if (state.result != null) {
            status.put("result", state.result);
        }
        return status;
    }

    private PatchAiSummary callAi(PatchSecurityAnalysisResult result) {
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException("环境变量 DASHSCOPE_API_KEY 未设置");
        }

        JSONObject requestBody = new JSONObject(new LinkedHashMap<>());
        requestBody.put("model", MODEL);
        requestBody.put("temperature", 0.1d);
        requestBody.put("messages", List.of(
            Map.of("role", "system", "content", provider.buildSystemPrompt()),
            Map.of("role", "user", "content", provider.buildUserPrompt(result))
        ));

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(API_URL))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey.trim())
            .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
            .POST(HttpRequest.BodyPublishers.ofString(requestBody.toJSONString(), StandardCharsets.UTF_8))
            .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException("AI请求失败，HTTP " + response.statusCode());
            }
            return parseAiResponse(response.body());
        } catch (IOException | InterruptedException e) {
            throw new BusinessException("AI调用失败: " + e.getMessage());
        }
    }

    private PatchAiSummary parseAiResponse(String body) {
        JSONObject root = JSON.parseObject(body);
        var choices = root.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new BusinessException("AI模型未返回有效结果");
        }
        String content = choices.getJSONObject(0).getJSONObject("message").getString("content");
        if (content == null || content.isBlank()) {
            throw new BusinessException("AI返回内容为空");
        }

        // Extract JSON from response
        String json = extractJson(content);
        return JSON.parseObject(json, PatchAiSummary.class);
    }

    private String extractJson(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstBreak = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstBreak >= 0 && lastFence > firstBreak) {
                trimmed = trimmed.substring(firstBreak + 1, lastFence).trim();
            }
        }
        if (trimmed.startsWith("{")) return trimmed;
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) return trimmed.substring(start, end + 1);
        return trimmed;
    }

    private String buildFallbackSummary(PatchSecurityAnalysisResult result) {
        int riskCount = result.getRisks() != null ? result.getRisks().size() : 0;
        long criticalCount = result.getRisks() != null
            ? result.getRisks().stream().filter(r -> "critical".equals(r.getSeverity())).count()
            : 0;
        long highCount = result.getRisks() != null
            ? result.getRisks().stream().filter(r -> "high".equals(r.getSeverity())).count()
            : 0;

        return String.format(
            "主机 %s 补丁安全分析完成。风险评分: %d（%s），共发现 %d 个风险项（严重: %d, 高危: %d）。",
            result.getHostName(), result.getRiskScore(), result.getRiskLevel(),
            riskCount, criticalCount, highCount
        );
    }

    private static class TaskState {
        final String taskId;
        final Long hostId;
        volatile String status;
        volatile String stageKey;
        volatile String stageText;
        volatile int progress;
        volatile String errorMessage;
        volatile PatchSecurityAnalysisResult result;

        TaskState(String taskId, Long hostId) {
            this.taskId = taskId;
            this.hostId = hostId;
            this.status = "PENDING";
            this.stageKey = "pending";
            this.stageText = "等待开始";
            this.progress = 0;
        }

        void update(String stageKey, String stageText, int progress) {
            this.stageKey = stageKey;
            this.stageText = stageText;
            this.progress = progress;
        }

        void setStatus(String status) { this.status = status; }
        void setErrorMessage(String msg) { this.errorMessage = msg; this.status = "FAILED"; }
        void setResult(PatchSecurityAnalysisResult r) { this.result = r; }
    }
}
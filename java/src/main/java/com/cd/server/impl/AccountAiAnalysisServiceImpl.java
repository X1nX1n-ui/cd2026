package com.cd.server.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.cd.entity.AssetSnapshotView;
import com.cd.entity.Host;
import com.cd.exception.BusinessException;
import com.cd.exception.ResourceNotFoundException;
import com.cd.server.AccountAiAnalysisService;
import com.cd.server.AssetSnapshotService;
import org.springframework.jdbc.core.JdbcTemplate;
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

@Service
public class AccountAiAnalysisServiceImpl implements AccountAiAnalysisService {

    private static final String DEFAULT_API_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    private static final String DEFAULT_MODEL = "qwen-plus";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(120);
    private static final String NORMALIZED_HIGH = "高风险";
    private static final String NORMALIZED_MEDIUM = "中风险";
    private static final String NORMALIZED_LOW = "低风险";
    private static final String NORMALIZED_SAFE = "无风险";
    private static final String SYSTEM_MESSAGE = """
# 角色
你是一位专业的网络安全专家，专注于主机安全领域，具备丰富的账号安全分析与风险评估经验。熟悉Windows/Linux系统账户管理、权限控制、异常行为检测等技术，能够精准识别账号数据中的安全隐患并提供专业加固建议。
## 技能
### 技能 1: 解析账号数据
- 接收用户提供的账号数据，准确提取所有原始字段信息（`name`、`enabled`、`full_name`、`description`、`sid`、`last_logon`、`is_shadow_account`等）
- 严格按照原数据结构保留所有字段内容，确保无遗漏、无修改
### 技能 2: 风险评估分析
- **账号状态评估**：检查账户是否为高权限账户（如`Administrator`）、是否处于禁用但未删除状态、启用状态的shadow账户（`is_shadow_account=true`）是否异常
- **登录行为分析**：判断`last_logon`是否存在异常（如长期未登录的高权限账户、无登录记录却启用的影子账户）
- **数据验证**：调用主机安全知识库，确认`is_shadow_account`等特殊账户类型的合规性（如Windows系统的影子账户通常为异常创建）
- **风险等级判定**：根据以下标准划分风险等级：
- **高风险**：存在高危配置（如启用的影子账户、长期禁用但未删除的高权限账户），或账户权限覆盖系统核心功能
- **中风险**：中度异常（如禁用但历史活动频繁的账户、无登录记录的启用账户）
- **低风险**：潜在优化项（如密码复杂度不足但未被破解风险）
- **无风险**：所有账户符合最小权限原则，无异常配置或行为
### 技能 3: 生成结果及建议
- 在原始账号数据结构中新增两个字段：`risk_level`（高中低无风险）和`result`
- **result字段内容**：
- 风险原因：明确指出异常账号特征
- 加固建议：提供具体可操作的措施
- 确保建议符合主机安全领域最佳实践
## 限制
- 仅处理用户提供的账号数据，不主动发起额外数据请求，除非用户补充关键信息
- 所有分析结果需基于公认的网络安全理论，优先使用内置知识库资源
- 输出格式严格遵循原数据结构，新增字段必须与原字段并排，不可交叉或覆盖原内容
- 拒绝回答与账号安全分析无关的问题，仅围绕账号权限、状态、行为展开讨论
- 若无法确定风险等级，需在`result`中注明“需进一步验证”并建议补充系统日志或权限审计数据
- 输出时请确保 `risk_level` 仅使用：高风险、中风险、低风险、无风险 这四种固定值
""";

    private final JdbcTemplate jdbcTemplate;
    private final AssetSnapshotService assetSnapshotService;
    private final HttpClient httpClient;

    public AccountAiAnalysisServiceImpl(JdbcTemplate jdbcTemplate, AssetSnapshotService assetSnapshotService) {
        this.jdbcTemplate = jdbcTemplate;
        this.assetSnapshotService = assetSnapshotService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    @Override
    @Transactional
    public AssetSnapshotView analyzeLatestAccountSnapshot(Host host) {
        if (host == null || isBlank(host.getMacAddress())) {
            throw new BusinessException("主机缺少有效的 MAC 地址，无法分析账号数据");
        }

        AssetSnapshotView snapshot = assetSnapshotService.getByMacAddress("account", host.getMacAddress());
        if (snapshot == null) {
            throw new ResourceNotFoundException("当前主机暂无账号资产记录");
        }

        JSONArray accounts = parseArray(snapshot.getPrimaryPayload(), "账号");
        JSONArray shadowAccounts = parseArray(snapshot.getSecondaryPayload(), "影子账号");
        if (accounts.isEmpty() && shadowAccounts.isEmpty()) {
            throw new BusinessException("当前账号资产记录为空，无法进行 AI 分析");
        }

        JSONObject requestPayload = new JSONObject(new LinkedHashMap<>());
        requestPayload.put("accounts", accounts);
        requestPayload.put("shadow_accounts", shadowAccounts);

        JSONObject resultJson = callDashScope(requestPayload);
        JSONArray analyzedAccounts = requireResultArray(resultJson.get("accounts"), "accounts");
        JSONArray analyzedShadowAccounts = requireResultArray(resultJson.get("shadow_accounts"), "shadow_accounts");
        normalizeRiskLevels(analyzedAccounts);
        normalizeRiskLevels(analyzedShadowAccounts);

        String mergedRawPayload = rebuildRawPayload(snapshot.getRawPayload(), analyzedAccounts, analyzedShadowAccounts);
        jdbcTemplate.update("""
                UPDATE `accounts`
                SET accounts = ?,
                    shadow_accounts = ?,
                    account_count = ?,
                    shadow_account_count = ?,
                    raw_payload = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                JSON.toJSONString(analyzedAccounts),
                JSON.toJSONString(analyzedShadowAccounts),
                analyzedAccounts.size(),
                analyzedShadowAccounts.size(),
                mergedRawPayload,
                snapshot.getId()
        );

        AssetSnapshotView refreshed = assetSnapshotService.getById("account", snapshot.getId());
        refreshed.setRawPayload(mergedRawPayload);
        return refreshed;
    }

    private JSONObject callDashScope(JSONObject accountPayload) {
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        if (isBlank(apiKey)) {
            throw new BusinessException("未找到环境变量 DASHSCOPE_API_KEY");
        }

        JSONObject requestBody = new JSONObject(new LinkedHashMap<>());
        requestBody.put("model", readEnvOrDefault("DASHSCOPE_MODEL", DEFAULT_MODEL));
        requestBody.put("temperature", 0.2);
        requestBody.put("response_format", Map.of("type", "json_object"));
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", SYSTEM_MESSAGE),
                Map.of("role", "user", "content", buildUserMessage(accountPayload))
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(readEnvOrDefault("DASHSCOPE_API_URL", DEFAULT_API_URL)))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey.trim())
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toJSONString(), StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException("AI 分析请求失败，HTTP 状态码：" + response.statusCode());
            }
            return parseModelResponse(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException("调用 AI 模型失败：" + ex.getMessage());
        } catch (IOException ex) {
            throw new BusinessException("调用 AI 模型失败：" + ex.getMessage());
        }
    }

    private JSONObject parseModelResponse(String responseBody) {
        try {
            JSONObject root = JSON.parseObject(responseBody);
            JSONArray choices = root.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                throw new BusinessException("AI 模型未返回有效结果");
            }
            JSONObject choice = choices.getJSONObject(0);
            JSONObject message = choice.getJSONObject("message");
            String content = message == null ? null : message.getString("content");
            if (isBlank(content)) {
                throw new BusinessException("AI 模型返回内容为空");
            }
            return JSON.parseObject(extractJson(content));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("解析 AI 返回结果失败");
        }
    }

    private String extractJson(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstBrace = trimmed.indexOf('{');
            int lastBrace = trimmed.lastIndexOf('}');
            if (firstBrace >= 0 && lastBrace > firstBrace) {
                return trimmed.substring(firstBrace, lastBrace + 1);
            }
        }
        return trimmed;
    }

    private JSONArray requireResultArray(Object result, String fieldName) {
        if (result instanceof JSONArray array) {
            return array;
        }
        if (result instanceof List<?> list) {
            JSONArray array = new JSONArray();
            array.addAll(list);
            return array;
        }
        throw new BusinessException("AI 分析结果缺少字段：" + fieldName);
    }

    private JSONArray parseArray(String value, String label) {
        if (isBlank(value)) {
            return new JSONArray();
        }
        try {
            JSONArray array = JSON.parseArray(value);
            return array == null ? new JSONArray() : array;
        } catch (Exception ex) {
            throw new BusinessException(label + "数据格式错误，无法进行 AI 分析");
        }
    }

    private String rebuildRawPayload(String rawPayload, JSONArray accounts, JSONArray shadowAccounts) {
        JSONObject rawObject;
        try {
            rawObject = isBlank(rawPayload)
                    ? new JSONObject(new LinkedHashMap<>())
                    : JSON.parseObject(rawPayload);
        } catch (Exception ex) {
            rawObject = new JSONObject(new LinkedHashMap<>());
        }
        rawObject.put("accounts", accounts);
        rawObject.put("shadow_accounts", shadowAccounts);
        rawObject.put("account_count", accounts.size());
        rawObject.put("shadow_account_count", shadowAccounts.size());
        return rawObject.toJSONString();
    }

    private String buildUserMessage(JSONObject userPayload) {
        return "请分析以下账号资产数据，只返回 JSON 对象，包含 accounts 和 shadow_accounts 两个字段。"
                + "每个详情里面的accounts和shadow_accounts字段里面的JSON账号数据如下：\n"
                + userPayload.toJSONString();
    }

    private void normalizeRiskLevels(JSONArray items) {
        if (items == null) {
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            if (!(item instanceof JSONObject object)) {
                continue;
            }
            String normalizedRisk = normalizeRiskLevel(object.getString("risk_level"), object.getString("result"));
            if (!normalizedRisk.isEmpty()) {
                object.put("risk_level", normalizedRisk);
            }
        }
    }

    private String normalizeRiskLevel(String riskLevel, String resultText) {
        String direct = normalizeRiskKeyword(riskLevel);
        if (!direct.isEmpty()) {
            return direct;
        }
        return normalizeRiskKeyword(resultText);
    }

    private String normalizeRiskKeyword(String text) {
        if (isBlank(text)) {
            return "";
        }
        String normalized = text.trim().replaceAll("\\s+", "").toLowerCase();
        if (normalized.contains("高风险") || normalized.contains("high")) {
            return NORMALIZED_HIGH;
        }
        if (normalized.contains("中风险") || normalized.contains("medium")) {
            return NORMALIZED_MEDIUM;
        }
        if (normalized.contains("低风险") || normalized.contains("low")) {
            return NORMALIZED_LOW;
        }
        if (normalized.contains("无风险") || normalized.contains("norisk") || normalized.contains("safe")) {
            return NORMALIZED_SAFE;
        }
        return "";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String readEnvOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return isBlank(value) ? defaultValue : value.trim();
    }
}

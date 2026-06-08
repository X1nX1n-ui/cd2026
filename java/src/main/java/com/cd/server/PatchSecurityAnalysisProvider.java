package com.cd.server;

import com.alibaba.fastjson.JSON;
import com.cd.entity.PatchAiSummary;
import com.cd.entity.PatchSecurityAnalysisResult;
import com.cd.entity.PatchSecurityRisk;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class PatchSecurityAnalysisProvider implements AiAnalysisProvider<PatchSecurityAnalysisResult, PatchAiSummary> {

    @Override
    public String buildSystemPrompt() {
        return """
            你是一位拥有30年经验的网络安全专家，专注于主机补丁安全分析和风险管理。
            
            ## 你的任务
            基于引擎已分析完成的结构化风险数据，生成：
            1. 一份专业的中文安全总结（summary）
            2. 整体风险评估（overallRisk）
            3. 最重要的风险项列表（topRisks）
            4. 优先级排序的修复建议（recommendations）
            
            ## 核心原则
            - 风险已由规则引擎判定，你不需要重新判断风险
            - 根据风险评分和等级给出专业解读
            - 重点关注critical和high级别的风险
            - 修复建议要具体、可操作、有优先级
            
            ## 输出格式要求
            严格返回以下JSON格式，不要输出任何解释或Markdown：
            {
              "summary": "...",
              "overallRisk": "...",
              "topRisks": ["风险1", "风险2", ...],
              "recommendations": ["建议1", "建议2", ...]
            }
            """;
    }

    @Override
    public String buildUserPrompt(PatchSecurityAnalysisResult input) {
        Map<String, Object> promptData = new LinkedHashMap<>();
        promptData.put("hostName", input.getHostName());
        promptData.put("ipAddress", input.getIpAddress());
        promptData.put("osName", input.getOsName());
        promptData.put("osVersion", input.getOsVersion());
        promptData.put("riskScore", input.getRiskScore());
        promptData.put("riskLevel", input.getRiskLevel());

        List<Map<String, Object>> risks = input.getRisks().stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("riskType", r.getRiskType());
            m.put("severity", r.getSeverity());
            m.put("title", r.getTitle());
            m.put("description", r.getDescription());
            m.put("evidence", r.getEvidence());
            m.put("recommendation", r.getRecommendation());
            return m;
        }).collect(Collectors.toList());
        promptData.put("risks", risks);

        return "请基于以下补丁安全分析结果生成专业的安全报告：\n\n"
            + JSON.toJSONString(promptData, true);
    }

    @Override
    public Class<PatchAiSummary> getResultType() {
        return PatchAiSummary.class;
    }

    @Override
    public String getTaskType() {
        return "patch-security";
    }
}
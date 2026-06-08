package com.cd.controller;

import com.cd.entity.PatchSecurityAnalysisResult;
import com.cd.entity.PatchSecurityResultEntity;
import com.cd.entity.PatchSecurityRisk;
import com.cd.entity.PatchSecurityRiskEntity;
import com.cd.mapper.PatchSecurityResultMapper;
import com.cd.mapper.HostMapper;
import com.cd.server.PatchSecurityAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/patch-analysis")
public class PatchAnalysisController {

    private static final Logger log = LoggerFactory.getLogger(PatchAnalysisController.class);

    private final PatchSecurityAnalysisService analysisService;
    private final PatchSecurityResultMapper resultMapper;
    private final HostMapper hostMapper;

    public PatchAnalysisController(PatchSecurityAnalysisService analysisService,
                                    PatchSecurityResultMapper resultMapper,
                                    HostMapper hostMapper) {
        this.analysisService = analysisService;
        this.resultMapper = resultMapper;
        this.hostMapper = hostMapper;
    }

    // ========== 风险主机列表（补丁安全页面直接用） ==========
    @GetMapping("/at-risk-hosts")
    @PreAuthorize("hasAuthority('threat:patch-security:view')")
    public List<PatchSecurityResultEntity> listAtRiskHosts(@RequestParam(required = false) String keyword) {
        log.info("[PATCH-ANALYSIS] Listing at-risk hosts, keyword={}", keyword);
        return resultMapper.findAtRiskHosts(keyword);
    }

    // ========== 单主机分析结果 ==========
    @GetMapping("/{hostId}")
    @PreAuthorize("hasAuthority('threat:patch-security:view')")
    public PatchSecurityAnalysisResult getAnalysis(@PathVariable Long hostId) {
        PatchSecurityResultEntity entity = resultMapper.findByHostId(hostId);
        if (entity == null) return null;

        List<PatchSecurityRiskEntity> riskEntities = resultMapper.findRisksByHostId(hostId);

        PatchSecurityAnalysisResult result = new PatchSecurityAnalysisResult();
        result.setHostId(entity.getHostId());
        result.setHostName(entity.getHostName());
        result.setIpAddress(entity.getIpAddress());
        result.setMacAddress(entity.getMacAddress());
        result.setOsName(entity.getOsName());
        result.setOsVersion(entity.getOsVersion());
        result.setRiskScore(entity.getRiskScore());
        result.setRiskLevel(entity.getRiskLevel());
        result.setSummary(entity.getAiSummary());

        List<PatchSecurityRisk> risks = riskEntities.stream().map(re -> {
            PatchSecurityRisk r = new PatchSecurityRisk();
            r.setRiskType(re.getRiskType());
            r.setSeverity(re.getSeverity());
            r.setTitle(re.getTitle());
            r.setDescription(re.getDescription());
            r.setEvidence(re.getEvidence());
            r.setRecommendation(re.getRecommendation());
            r.setRelatedPatches(re.getRelatedPatches() != null ? List.of(re.getRelatedPatches().split(",")) : List.of());
            r.setRelatedCves(re.getRelatedCves() != null ? List.of(re.getRelatedCves().split(",")) : List.of());
            return r;
        }).collect(Collectors.toList());
        result.setRisks(risks);
        return result;
    }

    // ========== 全部主机（含分析数据，无风险的主机也展示） ==========
    @GetMapping("/all-hosts")
    @PreAuthorize("hasAuthority('threat:patch-security:view')")
    public List<PatchSecurityResultEntity> listAllHosts(@RequestParam(required = false) String keyword) {
        log.info("[PATCH-ANALYSIS] Listing all hosts with analysis, keyword={}", keyword);
        return resultMapper.findAllHostsWithAnalysis(keyword);
    }

    // ========== 异步分析 ==========
    @PostMapping("/{hostId}/async")
    @PreAuthorize("hasAuthority('threat:patch-security:view')")
    public Map<String, Object> startAsync(@PathVariable Long hostId) {
        return analysisService.startAnalysisTask(hostId);
    }

    @GetMapping("/task/{taskId}")
    @PreAuthorize("hasAuthority('threat:patch-security:view')")
    public Map<String, Object> taskStatus(@PathVariable String taskId) {
        return analysisService.getTaskStatus(taskId);
    }
}
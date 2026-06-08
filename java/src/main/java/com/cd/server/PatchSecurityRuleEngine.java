package com.cd.server;

import com.cd.entity.Host;
import com.cd.entity.InstalledPatch;
import com.cd.entity.PatchCveMap;
import com.cd.entity.PatchSecurityAnalysisResult;
import com.cd.entity.PatchSecurityResultEntity;
import com.cd.entity.PatchSecurityRisk;
import com.cd.entity.PatchSecurityRiskEntity;
import com.cd.mapper.HostMapper;
import com.cd.mapper.InstalledPatchMapper;
import com.cd.mapper.PatchCveMapMapper;
import com.cd.mapper.PatchSecurityResultMapper;
import com.cd.server.PatchSecurityAnalysisProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class PatchSecurityRuleEngine {

    private static final Logger log = LoggerFactory.getLogger(PatchSecurityRuleEngine.class);

    private static final Map<String, Integer> SEVERITY_SCORE = Map.of(
        "critical", 40, "high", 20, "medium", 10, "low", 5
    );

    private static final Set<String> WINDOWS_EOL_VERSIONS = Set.of(
        "Windows 7", "Windows 8", "Windows 8.1",
        "Windows Server 2008", "Windows Server 2012",
        "Windows 10 1507", "Windows 10 1511", "Windows 10 1607",
        "Windows 10 1703", "Windows 10 1709", "Windows 10 1803",
        "Windows 10 1809", "Windows 10 1909", "Windows 10 2004",
        "Windows 10 20H2", "Windows 10 21H1"
    );

    private final PatchCveMapMapper patchCveMapMapper;
    private final InstalledPatchMapper installedPatchMapper;
    private final HostMapper hostMapper;
    private final PatchSecurityResultMapper resultMapper;
    private final PatchSecurityAnalysisProvider aiProvider;

    public PatchSecurityRuleEngine(PatchCveMapMapper patchCveMapMapper,
                                   InstalledPatchMapper installedPatchMapper,
                                   HostMapper hostMapper,
                                   PatchSecurityResultMapper resultMapper,
                                   PatchSecurityAnalysisProvider aiProvider) {
        this.patchCveMapMapper = patchCveMapMapper;
        this.installedPatchMapper = installedPatchMapper;
        this.hostMapper = hostMapper;
        this.resultMapper = resultMapper;
        this.aiProvider = aiProvider;
    }

    // ========== MQ触发入口：补丁入库后自动分析 ==========
    @Transactional
    public PatchSecurityAnalysisResult analyzeAndPersist(Host host) {
        log.info("[PATCH-ANALYSIS] Auto-analysis triggered for host={} mac={}", host.getHostname(), host.getMacAddress());
        PatchSecurityAnalysisResult result = analyzeHost(host);
        persistResult(result);
        log.info("[PATCH-ANALYSIS] Analysis persisted: host={} score={} level={} risks={}",
            host.getHostname(), result.getRiskScore(), result.getRiskLevel(), result.getRisks().size());
        return result;
    }

    // ========== 全量重分析入口（每日凌晨调度） ==========
    @Transactional
    public void analyzeAllHosts() {
        List<Long> hostIds = resultMapper.findAllHostIdsWithPatches();
        log.info("[PATCH-ANALYSIS] Full re-analysis starting for {} host(s)", hostIds.size());
        for (Long hostId : hostIds) {
            try {
                Host host = hostMapper.selectById(hostId);
                if (host != null) {
                    analyzeAndPersist(host);
                }
            } catch (Exception e) {
                log.error("[PATCH-ANALYSIS] Re-analysis failed for hostId={}: {}", hostId, e.getMessage());
            }
        }
        log.info("[PATCH-ANALYSIS] Full re-analysis completed");
    }

    // ========== 基于Host实体分析 ==========
    public PatchSecurityAnalysisResult analyzeHost(Host host) {
        if (host == null) throw new RuntimeException("Host is null");

        PatchSecurityAnalysisResult result = new PatchSecurityAnalysisResult();
        result.setHostId(host.getId());
        result.setHostName(host.getHostname());
        result.setIpAddress(host.getIpAddress());
        result.setMacAddress(host.getMacAddress());
        result.setOsName(host.getOsName());
        result.setOsVersion(host.getOsVersion());

        List<InstalledPatch> installedPatches = installedPatchMapper.selectByMacAddress(host.getMacAddress());
        Set<String> installedPatchIds = installedPatches.stream()
            .map(InstalledPatch::getPatchId).filter(Objects::nonNull).collect(Collectors.toSet());

        List<PatchSecurityRisk> risks = new ArrayList<>();
        risks.addAll(checkR1MissingPatches(host, installedPatchIds));
        risks.addAll(checkR3BadPatches(installedPatches));
        risks.addAll(checkR4NotEffective(installedPatches));
        risks.addAll(checkR5OutdatedPatches(installedPatches, installedPatchIds));
        risks.addAll(checkR6Eol(host));

        int totalScore = 0;
        for (PatchSecurityRisk r : risks) totalScore += SEVERITY_SCORE.getOrDefault(r.getSeverity(), 0);
        result.setRiskScore(totalScore);
        result.setRiskLevel(totalScore >= 81 ? "critical" : totalScore >= 51 ? "high" : totalScore >= 21 ? "medium" : "low");
        result.setRisks(risks);
        return result;
    }

    // ========== 持久化 ==========
    private void persistResult(PatchSecurityAnalysisResult result) {
        PatchSecurityResultEntity entity = new PatchSecurityResultEntity();
        entity.setHostId(result.getHostId());
        entity.setMacAddress(result.getMacAddress());
        entity.setRiskScore(result.getRiskScore());
        entity.setRiskLevel(result.getRiskLevel());

        List<PatchSecurityRisk> risks = result.getRisks();
        entity.setRiskCount(risks.size());
        entity.setCriticalCount((int) risks.stream().filter(r -> "critical".equals(r.getSeverity())).count());
        entity.setHighCount((int) risks.stream().filter(r -> "high".equals(r.getSeverity())).count());
        entity.setMediumCount((int) risks.stream().filter(r -> "medium".equals(r.getSeverity())).count());
        entity.setLowCount((int) risks.stream().filter(r -> "low".equals(r.getSeverity())).count());

        // Try AI summary
        try {
            String summary = callAiSummary(result);
            entity.setAiSummary(summary);
            result.setSummary(summary);
        } catch (Exception e) {
            log.warn("[PATCH-ANALYSIS] AI summary failed for host={}: {}", result.getHostName(), e.getMessage());
            entity.setAiSummary(buildFallbackSummary(result));
        }

        resultMapper.upsertResult(entity);
        // Get the persisted result ID for linking risks
        PatchSecurityResultEntity persisted = resultMapper.findByHostId(result.getHostId());
        Long resultId = persisted != null ? persisted.getId() : null;

        resultMapper.deleteRisksByHostId(result.getHostId());

        if (!risks.isEmpty()) {
            Long finalResultId = resultId;
            List<PatchSecurityRiskEntity> riskEntities = risks.stream().map(r -> {
                PatchSecurityRiskEntity re = new PatchSecurityRiskEntity();
                re.setResultId(finalResultId);
                re.setHostId(result.getHostId());
                re.setRiskType(r.getRiskType());
                re.setSeverity(r.getSeverity());
                re.setTitle(r.getTitle());
                re.setDescription(r.getDescription());
                re.setEvidence(r.getEvidence());
                re.setRecommendation(r.getRecommendation());
                re.setRelatedPatches(r.getRelatedPatches() != null ? String.join(",", r.getRelatedPatches()) : null);
                re.setRelatedCves(r.getRelatedCves() != null ? String.join(",", r.getRelatedCves()) : null);
                return re;
            }).collect(Collectors.toList());
            resultMapper.insertRisks(riskEntities);
        }
    }

    private String callAiSummary(PatchSecurityAnalysisResult result) {
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        if (apiKey == null || apiKey.isBlank()) return null;
        // Delegate to the analysis service impl for actual API call
        return null; // AI call handled in PatchSecurityAnalysisServiceImpl
    }

    private String buildFallbackSummary(PatchSecurityAnalysisResult result) {
        int c = (int) result.getRisks().stream().filter(r -> "critical".equals(r.getSeverity())).count();
        int h = (int) result.getRisks().stream().filter(r -> "high".equals(r.getSeverity())).count();
        return String.format("主机 %s：风险评分 %d（%s），发现 %d 个风险项（严重:%d, 高危:%d）。",
            result.getHostName(), result.getRiskScore(), result.getRiskLevel(), result.getRisks().size(), c, h);
    }

    // ========== R1-R6 规则 ==========
    private List<PatchSecurityRisk> checkR1MissingPatches(Host host, Set<String> installedPatchIds) {
        List<PatchSecurityRisk> risks = new ArrayList<>();
        String vendor = determineVendor(host.getOsName());
        List<String> patterns = determineProductPatterns(host);
        List<PatchCveMap> patches = patchCveMapMapper.findMissingPatchesForOs(vendor, patterns);
        for (PatchCveMap cve : patches) {
            if (!installedPatchIds.contains(cve.getPatchId())) {
                PatchSecurityRisk r = new PatchSecurityRisk();
                r.setRiskType("missing_patch");
                r.setSeverity(mapSeverity(cve.getCvssScore(), cve.getSeverity()));
                if (isKevOrActive(cve)) {
                    r.setSeverity(elevateSeverity(r.getSeverity()));
                    r.setTitle("缺失在野利用漏洞补丁: " + cve.getPatchId());
                    r.setDescription("主机缺少补丁 " + cve.getPatchId() + "（" + cve.getCveId() + "），该漏洞已被列入CISA KEV目录或在野活跃利用。CVSS: " + cve.getCvssScore());
                } else {
                    r.setTitle("缺失安全补丁: " + cve.getPatchId());
                    r.setDescription("缺少关键安全补丁 " + cve.getPatchId() + "（" + cve.getCveId() + "），CVSS: " + cve.getCvssScore() + "，影响: " + cve.getProduct());
                }
                r.setEvidence("缺失补丁: " + cve.getPatchId() + " | CVE: " + cve.getCveId() + " | CVSS: " + cve.getCvssScore());
                r.setRecommendation("立即安装补丁 " + cve.getPatchId());
                r.setRelatedPatches(List.of(cve.getPatchId()));
                r.setRelatedCves(List.of(cve.getCveId()));
                risks.add(r);
            }
        }
        return risks;
    }

    private List<PatchSecurityRisk> checkR3BadPatches(List<InstalledPatch> patches) {
        List<PatchSecurityRisk> risks = new ArrayList<>();
        for (InstalledPatch p : patches) {
            if (!"success".equalsIgnoreCase(p.getInstallStatus()) && p.getInstallStatus() != null) {
                PatchSecurityRisk r = new PatchSecurityRisk();
                r.setRiskType("bad_patch"); r.setSeverity("medium");
                r.setTitle("补丁安装异常: " + p.getPatchId());
                r.setDescription("补丁 " + p.getPatchId() + " 安装状态: " + p.getInstallStatus());
                r.setEvidence("安装状态: " + p.getInstallStatus() + " | 时间: " + p.getInstallTime());
                r.setRecommendation("尝试重新安装补丁 " + p.getPatchId());
                r.setRelatedPatches(List.of(p.getPatchId()));
                risks.add(r);
            }
            if ("invalid".equalsIgnoreCase(p.getSignatureStatus())) {
                PatchSecurityRisk r = new PatchSecurityRisk();
                r.setRiskType("bad_patch"); r.setSeverity("high");
                r.setTitle("补丁签名无效: " + p.getPatchId());
                r.setDescription("补丁 " + p.getPatchId() + " 数字签名校验失败，可能被篡改");
                r.setEvidence("签名状态: invalid");
                r.setRecommendation("从官方渠道重新下载安装 " + p.getPatchId());
                r.setRelatedPatches(List.of(p.getPatchId()));
                risks.add(r);
            }
        }
        return risks;
    }

    private List<PatchSecurityRisk> checkR4NotEffective(List<InstalledPatch> patches) {
        List<PatchSecurityRisk> risks = new ArrayList<>();
        for (InstalledPatch p : patches) {
            if (p.getRebootRequired() != null && p.getRebootRequired() == 1) {
                PatchSecurityRisk r = new PatchSecurityRisk();
                r.setRiskType("patch_not_effective"); r.setSeverity("high");
                r.setTitle("补丁需重启生效: " + p.getPatchId());
                r.setDescription("补丁 " + p.getPatchId() + " 已安装但需重启才能生效");
                r.setEvidence("补丁: " + p.getPatchId() + " | 需重启: 是");
                r.setRecommendation("尽快重启主机使补丁生效");
                r.setRelatedPatches(List.of(p.getPatchId()));
                risks.add(r);
            }
        }
        return risks;
    }

    private List<PatchSecurityRisk> checkR5OutdatedPatches(List<InstalledPatch> patches, Set<String> installedIds) {
        List<PatchSecurityRisk> risks = new ArrayList<>();
        for (InstalledPatch p : patches) {
            String sb = p.getSupersededBy();
            if (sb != null && !sb.isBlank() && !installedIds.contains(sb)) {
                PatchSecurityRisk r = new PatchSecurityRisk();
                r.setRiskType("outdated_patch_level"); r.setSeverity("medium");
                r.setTitle("补丁已过时: " + p.getPatchId());
                r.setDescription("补丁 " + p.getPatchId() + " 已被 " + sb + " 替代但未安装替代补丁");
                r.setEvidence("当前: " + p.getPatchId() + " | 替代: " + sb);
                r.setRecommendation("安装替代补丁 " + sb);
                r.setRelatedPatches(List.of(p.getPatchId(), sb));
                risks.add(r);
            }
        }
        return risks;
    }

    private List<PatchSecurityRisk> checkR6Eol(Host host) {
        String osName = defaultStr(host.getOsName());
        boolean eol = WINDOWS_EOL_VERSIONS.stream().anyMatch(osName::contains);
        String build = host.getOsBuild();
        if (osName.contains("Windows 10") && build != null) {
            try { if (Integer.parseInt(build.replaceAll("[^0-9]", "")) < 19045) eol = true; }
            catch (NumberFormatException ignored) {}
        }
        if (osName.contains("Server 2012") && !osName.contains("R2")) eol = true;
        if (eol) {
            PatchSecurityRisk r = new PatchSecurityRisk();
            r.setRiskType("unsupported_os"); r.setSeverity("critical");
            r.setTitle("操作系统已终止支持: " + osName);
            r.setDescription("主机OS " + osName + " (Build: " + defaultStr(build) + ") 已EOL，不再接收安全更新");
            r.setEvidence("OS: " + osName + " | Build: " + defaultStr(build));
            r.setRecommendation("立即升级到受支持的OS版本");
            return List.of(r);
        }
        return List.of();
    }

    // ========== Helpers ==========
    private String mapSeverity(Double cvss, String dbSev) {
        if (dbSev != null && !dbSev.isBlank()) return dbSev.toLowerCase();
        if (cvss == null) return "medium";
        if (cvss >= 9.0) return "critical";
        if (cvss >= 7.0) return "high";
        if (cvss >= 4.0) return "medium";
        return "low";
    }

    private String elevateSeverity(String s) {
        return switch (s) { case "critical" -> "critical"; case "high" -> "critical"; case "medium" -> "high"; default -> "high"; };
    }

    private boolean isKevOrActive(PatchCveMap cve) {
        return (cve.getKevFlag() != null && cve.getKevFlag() == 1) || "active".equalsIgnoreCase(cve.getExploitStatus());
    }

    private String determineVendor(String os) {
        if (os == null) return "Microsoft";
        String n = os.toLowerCase();
        if (n.contains("linux") || n.contains("ubuntu") || n.contains("centos")) return "Red Hat";
        return "Microsoft";
    }

    private List<String> determineProductPatterns(Host host) {
        String os = defaultStr(host.getOsName()).toLowerCase();
        if (os.contains("windows 11")) return List.of("Windows 11", "Windows");
        if (os.contains("windows 10")) return List.of("Windows 10", "Windows");
        if (os.contains("windows server")) return List.of("Windows Server", "Windows");
        if (os.contains("linux")) return List.of("Linux", "Kernel");
        return List.of("Windows", "Linux");
    }

    private String defaultStr(String s) { return s == null ? "" : s; }
}
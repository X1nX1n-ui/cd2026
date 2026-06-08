package com.cd.entity;

import java.util.List;

public class PatchSecurityAnalysisResult {

    private Long hostId;
    private String hostName;
    private String ipAddress;
    private String macAddress;
    private String osName;
    private String osVersion;
    private int riskScore;
    private String riskLevel;
    private String summary;
    private List<PatchSecurityRisk> risks;

    public Long getHostId() { return hostId; }
    public void setHostId(Long hostId) { this.hostId = hostId; }
    public String getHostName() { return hostName; }
    public void setHostName(String hostName) { this.hostName = hostName; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getMacAddress() { return macAddress; }
    public void setMacAddress(String macAddress) { this.macAddress = macAddress; }
    public String getOsName() { return osName; }
    public void setOsName(String osName) { this.osName = osName; }
    public String getOsVersion() { return osVersion; }
    public void setOsVersion(String osVersion) { this.osVersion = osVersion; }
    public int getRiskScore() { return riskScore; }
    public void setRiskScore(int riskScore) { this.riskScore = riskScore; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public List<PatchSecurityRisk> getRisks() { return risks; }
    public void setRisks(List<PatchSecurityRisk> risks) { this.risks = risks; }
}
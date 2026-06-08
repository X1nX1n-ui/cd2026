package com.cd.entity;

import java.util.List;

public class PatchSecurityRisk {

    private String riskType;
    private String severity;
    private String title;
    private String description;
    private String evidence;
    private String recommendation;
    private List<String> relatedPatches;
    private List<String> relatedCves;

    public String getRiskType() { return riskType; }
    public void setRiskType(String riskType) { this.riskType = riskType; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getEvidence() { return evidence; }
    public void setEvidence(String evidence) { this.evidence = evidence; }
    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }
    public List<String> getRelatedPatches() { return relatedPatches; }
    public void setRelatedPatches(List<String> relatedPatches) { this.relatedPatches = relatedPatches; }
    public List<String> getRelatedCves() { return relatedCves; }
    public void setRelatedCves(List<String> relatedCves) { this.relatedCves = relatedCves; }
}
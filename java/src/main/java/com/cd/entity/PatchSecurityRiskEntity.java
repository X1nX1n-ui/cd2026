package com.cd.entity;

import java.time.LocalDateTime;

public class PatchSecurityRiskEntity {

    private Long id;
    private Long resultId;
    private Long hostId;
    private String riskType;
    private String severity;
    private String title;
    private String description;
    private String evidence;
    private String recommendation;
    private String relatedPatches;
    private String relatedCves;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getResultId() { return resultId; }
    public void setResultId(Long resultId) { this.resultId = resultId; }
    public Long getHostId() { return hostId; }
    public void setHostId(Long hostId) { this.hostId = hostId; }
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
    public String getRelatedPatches() { return relatedPatches; }
    public void setRelatedPatches(String relatedPatches) { this.relatedPatches = relatedPatches; }
    public String getRelatedCves() { return relatedCves; }
    public void setRelatedCves(String relatedCves) { this.relatedCves = relatedCves; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
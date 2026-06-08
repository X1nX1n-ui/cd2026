package com.cd.entity;

import java.util.List;

public class PatchAiSummary {

    private String summary;
    private String overallRisk;
    private List<String> topRisks;
    private List<String> recommendations;

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getOverallRisk() { return overallRisk; }
    public void setOverallRisk(String overallRisk) { this.overallRisk = overallRisk; }
    public List<String> getTopRisks() { return topRisks; }
    public void setTopRisks(List<String> topRisks) { this.topRisks = topRisks; }
    public List<String> getRecommendations() { return recommendations; }
    public void setRecommendations(List<String> recommendations) { this.recommendations = recommendations; }
}
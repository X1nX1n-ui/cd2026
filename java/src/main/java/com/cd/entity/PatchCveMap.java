package com.cd.entity;

public class PatchCveMap {

    private Long id;
    private String patchId;
    private String cveId;
    private String vendor;
    private String product;
    private String affectedVersionRange;
    private String fixedVersion;
    private String fixType;
    private String exploitStatus;
    private Integer kevFlag;
    private Double cvssScore;
    private String severity;
    private String referenceUrl;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPatchId() { return patchId; }
    public void setPatchId(String patchId) { this.patchId = patchId; }
    public String getCveId() { return cveId; }
    public void setCveId(String cveId) { this.cveId = cveId; }
    public String getVendor() { return vendor; }
    public void setVendor(String vendor) { this.vendor = vendor; }
    public String getProduct() { return product; }
    public void setProduct(String product) { this.product = product; }
    public String getAffectedVersionRange() { return affectedVersionRange; }
    public void setAffectedVersionRange(String affectedVersionRange) { this.affectedVersionRange = affectedVersionRange; }
    public String getFixedVersion() { return fixedVersion; }
    public void setFixedVersion(String fixedVersion) { this.fixedVersion = fixedVersion; }
    public String getFixType() { return fixType; }
    public void setFixType(String fixType) { this.fixType = fixType; }
    public String getExploitStatus() { return exploitStatus; }
    public void setExploitStatus(String exploitStatus) { this.exploitStatus = exploitStatus; }
    public Integer getKevFlag() { return kevFlag; }
    public void setKevFlag(Integer kevFlag) { this.kevFlag = kevFlag; }
    public Double getCvssScore() { return cvssScore; }
    public void setCvssScore(Double cvssScore) { this.cvssScore = cvssScore; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getReferenceUrl() { return referenceUrl; }
    public void setReferenceUrl(String referenceUrl) { this.referenceUrl = referenceUrl; }
}
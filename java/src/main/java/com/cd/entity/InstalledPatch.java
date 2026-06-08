package com.cd.entity;

import java.time.LocalDateTime;

public class InstalledPatch {

    private Long id;
    private String macAddress;
    private String patchId;
    private String patchType;
    private String productName;
    private String productVersion;
    private LocalDateTime installTime;
    private String installStatus;
    private String source;
    private String signatureStatus;
    private Integer rebootRequired;
    private String supersededBy;
    private Integer isSecurityPatch;
    private String rawData;
    private LocalDateTime scanTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getMacAddress() { return macAddress; }
    public void setMacAddress(String macAddress) { this.macAddress = macAddress; }
    public String getPatchId() { return patchId; }
    public void setPatchId(String patchId) { this.patchId = patchId; }
    public String getPatchType() { return patchType; }
    public void setPatchType(String patchType) { this.patchType = patchType; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getProductVersion() { return productVersion; }
    public void setProductVersion(String productVersion) { this.productVersion = productVersion; }
    public LocalDateTime getInstallTime() { return installTime; }
    public void setInstallTime(LocalDateTime installTime) { this.installTime = installTime; }
    public String getInstallStatus() { return installStatus; }
    public void setInstallStatus(String installStatus) { this.installStatus = installStatus; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getSignatureStatus() { return signatureStatus; }
    public void setSignatureStatus(String signatureStatus) { this.signatureStatus = signatureStatus; }
    public Integer getRebootRequired() { return rebootRequired; }
    public void setRebootRequired(Integer rebootRequired) { this.rebootRequired = rebootRequired; }
    public String getSupersededBy() { return supersededBy; }
    public void setSupersededBy(String supersededBy) { this.supersededBy = supersededBy; }
    public Integer getIsSecurityPatch() { return isSecurityPatch; }
    public void setIsSecurityPatch(Integer isSecurityPatch) { this.isSecurityPatch = isSecurityPatch; }
    public String getRawData() { return rawData; }
    public void setRawData(String rawData) { this.rawData = rawData; }
    public LocalDateTime getScanTime() { return scanTime; }
    public void setScanTime(LocalDateTime scanTime) { this.scanTime = scanTime; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

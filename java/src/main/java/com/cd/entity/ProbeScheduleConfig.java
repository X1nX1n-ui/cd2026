package com.cd.entity;

import java.time.LocalDateTime;

public class ProbeScheduleConfig {

    private Long id;
    private Boolean enabled;
    private String cronExpression;
    private String targetType;
    private String targetHostIds;
    private Integer probeAccount;
    private Integer probeService;
    private Integer probeProcess;
    private Integer probeApp;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getCronExpression() { return cronExpression; }
    public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public String getTargetHostIds() { return targetHostIds; }
    public void setTargetHostIds(String targetHostIds) { this.targetHostIds = targetHostIds; }
    public Integer getProbeAccount() { return probeAccount; }
    public void setProbeAccount(Integer probeAccount) { this.probeAccount = probeAccount; }
    public Integer getProbeService() { return probeService; }
    public void setProbeService(Integer probeService) { this.probeService = probeService; }
    public Integer getProbeProcess() { return probeProcess; }
    public void setProbeProcess(Integer probeProcess) { this.probeProcess = probeProcess; }
    public Integer getProbeApp() { return probeApp; }
    public void setProbeApp(Integer probeApp) { this.probeApp = probeApp; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public boolean isProbeAll() {
        return probeAccount != null && probeAccount == 1
            && probeService != null && probeService == 1
            && probeProcess != null && probeProcess == 1
            && probeApp != null && probeApp == 1;
    }
}

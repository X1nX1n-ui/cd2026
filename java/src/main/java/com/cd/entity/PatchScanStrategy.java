package com.cd.entity;

import java.time.LocalDateTime;

public class PatchScanStrategy {

    private Long id;
    private Integer enabled;
    private String cronExpression;
    private String targetType;
    private String targetHostIds;
    private Integer timeoutMinutes;
    private Integer concurrency;
    private Integer retryCount;
    private Integer aiAnalysisEnabled;
    private LocalDateTime lastExecutedAt;
    private LocalDateTime nextExecutionAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    public String getCronExpression() { return cronExpression; }
    public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public String getTargetHostIds() { return targetHostIds; }
    public void setTargetHostIds(String targetHostIds) { this.targetHostIds = targetHostIds; }
    public Integer getTimeoutMinutes() { return timeoutMinutes; }
    public void setTimeoutMinutes(Integer timeoutMinutes) { this.timeoutMinutes = timeoutMinutes; }
    public Integer getConcurrency() { return concurrency; }
    public void setConcurrency(Integer concurrency) { this.concurrency = concurrency; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public Integer getAiAnalysisEnabled() { return aiAnalysisEnabled; }
    public void setAiAnalysisEnabled(Integer aiAnalysisEnabled) { this.aiAnalysisEnabled = aiAnalysisEnabled; }
    public LocalDateTime getLastExecutedAt() { return lastExecutedAt; }
    public void setLastExecutedAt(LocalDateTime lastExecutedAt) { this.lastExecutedAt = lastExecutedAt; }
    public LocalDateTime getNextExecutionAt() { return nextExecutionAt; }
    public void setNextExecutionAt(LocalDateTime nextExecutionAt) { this.nextExecutionAt = nextExecutionAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
package com.cd.entity;

import java.time.LocalDateTime;

public class AssetSnapshotView {

    private Long id;
    private String macAddress;
    private String primaryPayload;
    private String secondaryPayload;
    private Integer primaryCount;
    private Integer secondaryCount;
    private String rawPayload;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMacAddress() {
        return macAddress;
    }

    public void setMacAddress(String macAddress) {
        this.macAddress = macAddress;
    }

    public String getPrimaryPayload() {
        return primaryPayload;
    }

    public void setPrimaryPayload(String primaryPayload) {
        this.primaryPayload = primaryPayload;
    }

    public String getSecondaryPayload() {
        return secondaryPayload;
    }

    public void setSecondaryPayload(String secondaryPayload) {
        this.secondaryPayload = secondaryPayload;
    }

    public Integer getPrimaryCount() {
        return primaryCount;
    }

    public void setPrimaryCount(Integer primaryCount) {
        this.primaryCount = primaryCount;
    }

    public Integer getSecondaryCount() {
        return secondaryCount;
    }

    public void setSecondaryCount(Integer secondaryCount) {
        this.secondaryCount = secondaryCount;
    }

    public String getRawPayload() {
        return rawPayload;
    }

    public void setRawPayload(String rawPayload) {
        this.rawPayload = rawPayload;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

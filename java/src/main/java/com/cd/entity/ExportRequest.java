package com.cd.entity;

/**
 * Export request parameters from frontend.
 */
public class ExportRequest {

    private Long hostId;
    private String assetType;   // account, service, process, app
    private String format;      // csv, json, markdown

    public Long getHostId() {
        return hostId;
    }

    public void setHostId(Long hostId) {
        this.hostId = hostId;
    }

    public String getAssetType() {
        return assetType;
    }

    public void setAssetType(String assetType) {
        this.assetType = assetType;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }
}

package com.cd.entity;

import java.util.List;

public class BatchExportRequest {

    private List<Long> hostIds;
    private List<String> assetTypes;
    private String format;

    public List<Long> getHostIds() { return hostIds; }
    public void setHostIds(List<Long> hostIds) { this.hostIds = hostIds; }
    public List<String> getAssetTypes() { return assetTypes; }
    public void setAssetTypes(List<String> assetTypes) { this.assetTypes = assetTypes; }
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
}
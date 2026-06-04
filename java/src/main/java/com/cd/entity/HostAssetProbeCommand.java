package com.cd.entity;

public class HostAssetProbeCommand {

    private Integer account;
    private Integer service;
    private Integer process;
    private Integer app;
    private String macAddress;
    private String type;

    public Integer getAccount() {
        return account;
    }

    public void setAccount(Integer account) {
        this.account = account;
    }

    public Integer getService() {
        return service;
    }

    public void setService(Integer service) {
        this.service = service;
    }

    public Integer getProcess() {
        return process;
    }

    public void setProcess(Integer process) {
        this.process = process;
    }

    public Integer getApp() {
        return app;
    }

    public void setApp(Integer app) {
        this.app = app;
    }

    public String getMacAddress() {
        return macAddress;
    }

    public void setMacAddress(String macAddress) {
        this.macAddress = macAddress;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}

package com.cd.entity;

public class HostAssetDetailView {

    private Host host;
    private AssetSnapshotView account;
    private AssetSnapshotView service;
    private AssetSnapshotView process;
    private AssetSnapshotView app;

    public Host getHost() {
        return host;
    }

    public void setHost(Host host) {
        this.host = host;
    }

    public AssetSnapshotView getAccount() {
        return account;
    }

    public void setAccount(AssetSnapshotView account) {
        this.account = account;
    }

    public AssetSnapshotView getService() {
        return service;
    }

    public void setService(AssetSnapshotView service) {
        this.service = service;
    }

    public AssetSnapshotView getProcess() {
        return process;
    }

    public void setProcess(AssetSnapshotView process) {
        this.process = process;
    }

    public AssetSnapshotView getApp() {
        return app;
    }

    public void setApp(AssetSnapshotView app) {
        this.app = app;
    }
}

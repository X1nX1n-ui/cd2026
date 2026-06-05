package com.cd.controller;

import com.cd.entity.Host;
import com.cd.entity.HostAssetDetailView;
import com.cd.entity.HostAssetProbeCommand;
import com.cd.entity.PageResult;
import com.cd.server.AccountAiAnalysisService;
import com.cd.server.AssetSnapshotService;
import com.cd.server.HostProbeCommandService;
import com.cd.server.HostService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/hosts")
public class HostController {

    private final HostService hostService;
    private final HostProbeCommandService hostProbeCommandService;
    private final AssetSnapshotService assetSnapshotService;
    private final AccountAiAnalysisService accountAiAnalysisService;

    public HostController(HostService hostService,
                          HostProbeCommandService hostProbeCommandService,
                          AssetSnapshotService assetSnapshotService,
                          AccountAiAnalysisService accountAiAnalysisService) {
        this.hostService = hostService;
        this.hostProbeCommandService = hostProbeCommandService;
        this.assetSnapshotService = assetSnapshotService;
        this.accountAiAnalysisService = accountAiAnalysisService;
    }

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('asset:host:view')")
    public PageResult<Host> page(@RequestParam(defaultValue = "1") int pageNo,
                                 @RequestParam(defaultValue = "10") int pageSize,
                                 @RequestParam(required = false) String keyword,
                                 @RequestParam(required = false) String status) {
        return hostService.page(pageNo, pageSize, keyword, status);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('asset:host:view')")
    public Host getById(@PathVariable Long id) {
        return hostService.getById(id);
    }

    @GetMapping("/{id}/asset-detail")
    @PreAuthorize("hasAuthority('asset:host:view')")
    public HostAssetDetailView getAssetDetail(@PathVariable Long id) {
        Host host = hostService.getById(id);
        HostAssetDetailView detailView = new HostAssetDetailView();
        detailView.setHost(host);
        detailView.setAccount(assetSnapshotService.getByMacAddress("account", host.getMacAddress()));
        detailView.setService(assetSnapshotService.getByMacAddress("service", host.getMacAddress()));
        detailView.setProcess(assetSnapshotService.getByMacAddress("process", host.getMacAddress()));
        detailView.setApp(assetSnapshotService.getByMacAddress("app", host.getMacAddress()));
        return detailView;
    }

    @GetMapping("/{id}/asset-history/{assetType}")
    @PreAuthorize("hasAuthority('asset:host:view')")
    public List<com.cd.entity.AssetSnapshotView> getAssetHistory(@PathVariable Long id,
                                                                 @PathVariable String assetType) {
        Host host = hostService.getById(id);
        return assetSnapshotService.listByMacAddress(assetType, host.getMacAddress());
    }

    @PostMapping("/{id}/asset-detail/account/analyze")
    @PreAuthorize("hasAuthority('asset:host:view')")
    public com.cd.entity.AssetSnapshotView analyzeAccountAsset(@PathVariable Long id) {
        Host host = hostService.getById(id);
        return accountAiAnalysisService.analyzeLatestAccountSnapshot(host);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('asset:host:create')")
    public Host create(@RequestBody Host host) {
        return hostService.create(host);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('asset:host:update')")
    public Host update(@PathVariable Long id, @RequestBody Host host) {
        host.setId(id);
        return hostService.update(host);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('asset:host:delete')")
    public void delete(@PathVariable Long id) {
        hostService.deleteById(id);
    }

    @PostMapping("/{id}/asset-probe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('asset:host:view')")
    public void sendAssetProbeCommand(@PathVariable Long id, @RequestBody HostAssetProbeCommand command) {
        Host host = hostService.getById(id);
        command.setHostName(host.getHostname());
        command.setMacAddress(host.getMacAddress());
        hostProbeCommandService.sendAssetProbeCommand(command);
    }
}

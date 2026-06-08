package com.cd.controller;

import com.cd.entity.ProbeScheduleConfig;
import com.cd.server.ProbeScheduleService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/probe-schedule")
public class ProbeScheduleController {

    private final ProbeScheduleService probeScheduleService;

    public ProbeScheduleController(ProbeScheduleService probeScheduleService) {
        this.probeScheduleService = probeScheduleService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('asset:host:view')")
    public ProbeScheduleConfig getConfig() {
        return probeScheduleService.getConfig();
    }

    @PutMapping
    @PreAuthorize("hasAnyAuthority('asset:host:view','asset:probe-schedule:manage')")
    public ProbeScheduleConfig saveConfig(@RequestBody ProbeScheduleConfig config) {
        return probeScheduleService.saveConfig(config);
    }

    @PostMapping("/execute-now")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyAuthority('asset:host:view','asset:probe-schedule:manage')")
    public void executeNow() {
        probeScheduleService.executeNow();
    }
}

package com.cd.controller;

import com.cd.entity.PatchScanStrategy;
import com.cd.server.PatchScanService;
import com.cd.server.impl.PatchScanServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/patch-security")
public class PatchSecurityController {

    private static final Logger log = LoggerFactory.getLogger(PatchSecurityController.class);

    private final PatchScanService patchScanService;

    public PatchSecurityController(PatchScanService patchScanService) {
        this.patchScanService = patchScanService;
    }

    @GetMapping("/strategy")
    @PreAuthorize("hasAuthority('threat:patch-security:view')")
    public PatchScanStrategy getStrategy() {
        return patchScanService.getConfig();
    }

    @PutMapping("/strategy")
    @PreAuthorize("hasAuthority('threat:patch-security:view')")
    public PatchScanStrategy saveStrategy(@RequestBody PatchScanStrategy config) {
        return patchScanService.saveConfig(config);
    }

    @PostMapping("/execute-now")
    @PreAuthorize("hasAuthority('threat:patch-security:view')")
    public java.util.Map<String, Object> executeNow() {
        log.info("[PATCH-CTRL] Received execute-now request from frontend");
        String taskId = patchScanService.executeNow();
        log.info("[PATCH-CTRL] executeNow completed, taskId={}", taskId);
        java.util.Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("taskId", taskId);
        response.put("status", "DISPATCHED");
        response.put("message", "扫描指令已下发");
        return response;
    }

    @GetMapping("/scan-progress/{taskId}")
    @PreAuthorize("hasAuthority('threat:patch-security:view')")
    public java.util.Map<String, Object> scanProgress(@PathVariable String taskId) {
        PatchScanServiceImpl impl = (PatchScanServiceImpl) patchScanService;
        PatchScanServiceImpl.ScanTask task = impl.getScanTask(taskId);
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        if (task == null) {
            result.put("status", "NOT_FOUND");
            result.put("message", "任务不存在或已过期");
            return result;
        }
        result.put("taskId", task.taskId);
        result.put("status", task.status);
        result.put("stageText", task.stageText);
        result.put("progress", task.progress);
        result.put("patchCount", task.patchCount);
        result.put("hostCount", task.hostCount);
        return result;
    }
}

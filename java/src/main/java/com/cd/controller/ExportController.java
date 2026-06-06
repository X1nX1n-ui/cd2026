package com.cd.controller;

import com.cd.entity.ExportRequest;
import com.cd.entity.ExportTask;
import com.cd.security.AuthenticatedUser;
import com.cd.security.SecurityUtils;
import com.cd.server.ExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("/api/assets")
public class ExportController {

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    /**
     * Start an export task for a specific host''s asset type.
     */
    @PostMapping("/export")
    @PreAuthorize("hasAuthority('asset:host:view')")
    public ExportTask startExport(@RequestBody ExportRequest request) {
        AuthenticatedUser user = SecurityUtils.currentUser();
        boolean isAdmin = isAdminUser(user);
        return exportService.startExport(request, user.getUserId(), isAdmin);
    }

    /**
     * Poll the export task progress.
     */
    @GetMapping("/export/{taskId}")
    @PreAuthorize("hasAuthority('asset:host:view')")
    public ExportTask getTaskProgress(@PathVariable String taskId) {
        return exportService.getTask(taskId);
    }

    /**
     * Download the completed export file.
     */
    @GetMapping("/export/{taskId}/download")
    @PreAuthorize("hasAuthority('asset:host:view')")
    public ResponseEntity<byte[]> downloadFile(@PathVariable String taskId) {
        ExportTask task = exportService.getTask(taskId);
        if (!"COMPLETED".equals(task.getStatus())) {
            return ResponseEntity.badRequest().build();
        }

        byte[] fileBytes = Base64.getDecoder().decode(task.getFileData());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(task.getContentType()));
        headers.setContentDispositionFormData("attachment", task.getFileName());
        headers.set("Content-MD5", task.getMd5Hash());

        return ResponseEntity.ok().headers(headers).body(fileBytes);
    }

    /**
     * Retry a failed export task.
     */
    @PostMapping("/export/{taskId}/retry")
    @PreAuthorize("hasAuthority('asset:host:view')")
    public ExportTask retryExport(@PathVariable String taskId) {
        return exportService.resumeTask(taskId);
    }

    private boolean isAdminUser(AuthenticatedUser user) {
        if (user.getLoginUser() == null || user.getLoginUser().getRoleCodes() == null) {
            return false;
        }
        return user.getLoginUser().getRoleCodes().stream()
                .anyMatch(code -> "admin".equalsIgnoreCase(code) || "super_admin".equalsIgnoreCase(code));
    }
}

package com.cd.controller;

import com.cd.entity.BatchExportRequest;
import com.cd.entity.ExportRequest;
import com.cd.entity.ExportTask;
import com.cd.security.AuthenticatedUser;
import com.cd.security.SecurityUtils;
import com.cd.server.ExportService;
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
    @PreAuthorize("hasAnyAuthority('asset:host:view','asset:host:export')")
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
    public void downloadFile(@PathVariable String taskId, jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        ExportTask task = exportService.getTask(taskId);
        if (!"COMPLETED".equals(task.getStatus())) {
            response.sendError(400, "?????");
            return;
        }

        String fileData = task.getFileData();
        if (fileData == null || fileData.isEmpty()) {
            response.sendError(400, "??????");
            return;
        }

        byte[] fileBytes;
        try {
            fileBytes = Base64.getDecoder().decode(fileData);
        } catch (IllegalArgumentException e) {
            response.sendError(400, "??????");
            return;
        }

        String contentType = task.getContentType();
        if (contentType == null || contentType.isEmpty()) {
            contentType = "application/octet-stream";
        }
        response.setContentType(contentType);
        response.setCharacterEncoding("UTF-8");

        String fileName = task.getFileName();
        if (fileName == null || fileName.isEmpty()) {
            fileName = "export";
        }
        response.setHeader("Content-Disposition", "attachment; filename=\"" + java.net.URLEncoder.encode(fileName, "UTF-8").replace("+", "%20") + "\"");

        String md5 = task.getMd5Hash();
        if (md5 != null && !md5.isEmpty()) {
            response.setHeader("Content-MD5", md5);
        }

        response.setContentLength(fileBytes.length);
        response.getOutputStream().write(fileBytes);
        response.getOutputStream().flush();
    }

    /**
     * Retry a failed export task.
     */
    @PostMapping("/export/batch")
    @PreAuthorize("hasAnyAuthority('asset:host:view','asset:host:export')")
    public ExportTask startBatchExport(@RequestBody BatchExportRequest request) {
        AuthenticatedUser user = SecurityUtils.currentUser();
        boolean isAdmin = isAdminUser(user);
        return exportService.startBatchExport(request, user.getUserId(), isAdmin);
    }

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

package com.cd.server;

import com.cd.entity.ExportRequest;
import com.cd.entity.ExportTask;

/**
 * Service for exporting host asset inventory as CSV/JSON/Markdown
 * with data masking, MD5 integrity check, and permission-aware scoping.
 */
public interface ExportService {

    /**
     * Start an async export task. Returns the taskId for polling.
     */
    ExportTask startExport(ExportRequest request, Long userId, boolean isAdmin);

    /**
     * Poll the current state of an export task.
     */
    ExportTask getTask(String taskId);

    /**
     * Try to resume a previously failed or incomplete task.
     */
    ExportTask resumeTask(String taskId);

    ExportTask startBatchExport(com.cd.entity.BatchExportRequest request, Long userId, boolean isAdmin);
}

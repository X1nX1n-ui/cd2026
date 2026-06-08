package com.cd.server.impl;

import com.cd.entity.AssetSnapshotView;
import com.cd.entity.BatchExportRequest;
import com.cd.entity.ExportRequest;
import com.cd.entity.ExportTask;
import com.cd.entity.Host;
import com.cd.exception.BusinessException;
import com.cd.exception.ResourceNotFoundException;
import com.cd.mapper.HostMapper;
import com.cd.server.AssetSnapshotService;
import com.cd.server.ExportService;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class ExportServiceImpl implements ExportService {

    private static final int CHUNK_SIZE = 1000;
    private static final Map<String, ExportTask> TASK_STORE = new ConcurrentHashMap<>();

    // Fields whose values contain sensitive data and should be masked
    private static final List<String> SENSITIVE_FIELD_NAMES = List.of(
            "password", "passwd", "pwd", "secret", "key", "token",
            "private_key", "api_key", "access_key", "credential"
    );

    // Fields that contain IP addresses
    private static final List<String> IP_FIELD_NAMES = List.of(
            "ip", "ip_address", "ipAddress", "ipaddr", "remote_ip", "local_ip"
    );

    private static final Map<String, String> ASSET_LABEL_MAP = Map.of(
            "account", "\u8D26\u53F7",
            "service", "\u670D\u52A1",
            "process", "\u8FDB\u7A0B",
            "app", "\u5B89\u88C5\u7A0B\u5E8F"
    );

    private final HostMapper hostMapper;
    private final AssetSnapshotService assetSnapshotService;

    public ExportServiceImpl(HostMapper hostMapper,
                             AssetSnapshotService assetSnapshotService) {
        this.hostMapper = hostMapper;
        this.assetSnapshotService = assetSnapshotService;
    }

    @Override
    public ExportTask startExport(ExportRequest request, Long userId, boolean isAdmin) {
        String taskId = UUID.randomUUID().toString().replace("-", "");
        String normalizedType = normalizeAssetType(request.getAssetType());
        String normalizedFormat = normalizeFormat(request.getFormat());

        ExportTask task = new ExportTask();
        task.setTaskId(taskId);
        task.setStatus("PENDING");
        task.setProgress(0);
        task.setFormat(normalizedFormat);
        task.setAssetType(normalizedType);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        task.setMessage("\u6B63\u5728\u51C6\u5907\u5BFC\u51FA...");

        TASK_STORE.put(taskId, task);

        // Run export asynchronously
        executeExport(task, request.getHostId(), normalizedType, normalizedFormat, isAdmin);

        return task;
    }

    @Override
    public ExportTask getTask(String taskId) {
        ExportTask task = TASK_STORE.get(taskId);
        if (task == null) {
            throw new ResourceNotFoundException("\u5BFC\u51FA\u4EFB\u52A1\u4E0D\u5B58\u5728\u6216\u5DF2\u8FC7\u671F: " + taskId);
        }
        return task;
    }

    @Override
    public ExportTask resumeTask(String taskId) {
        ExportTask existing = TASK_STORE.get(taskId);
        if (existing == null) {
            throw new ResourceNotFoundException("\u5BFC\u51FA\u4EFB\u52A1\u4E0D\u5B58\u5728: " + taskId);
        }
        if (!"FAILED".equals(existing.getStatus())) {
            throw new BusinessException("\u53EA\u80FD\u6062\u590D\u5931\u8D25\u7684\u5BFC\u51FA\u4EFB\u52A1");
        }
        existing.setStatus("PENDING");
        existing.setProgress(0);
        existing.setMessage("\u6B63\u5728\u91CD\u65B0\u5BFC\u51FA...");
        existing.setUpdatedAt(LocalDateTime.now());

        // Re-execute: we need hostId and assetType from stored task
        executeExport(existing, null, existing.getAssetType(), existing.getFormat(), true);
        return existing;
    }

    @Async
    public void executeExport(ExportTask task, Long hostId, String assetType, String format, boolean isAdmin) {
        try {
            task.setStatus("PROCESSING");
            task.setMessage("\u6B63\u5728\u67E5\u8BE2\u4E3B\u673A\u4FE1\u606F...");
            task.setProgress(5);
            task.setUpdatedAt(LocalDateTime.now());

            Host host = hostMapper.selectById(hostId);
            if (host == null) {
                failTask(task, "\u4E3B\u673A\u4E0D\u5B58\u5728");
                return;
            }
            task.setHostName(host.getHostname() != null ? host.getHostname() : "\u672A\u547D\u540D\u4E3B\u673A");

            task.setMessage("\u6B63\u5728\u83B7\u53D6\u8D44\u4EA7\u6570\u636E...");
            task.setProgress(15);
            task.setUpdatedAt(LocalDateTime.now());

            List<AssetSnapshotView> snapshots = assetSnapshotService.listByMacAddress(assetType, host.getMacAddress());
            if (snapshots.isEmpty()) {
                failTask(task, "\u8BE5\u4E3B\u673A\u6682\u65E0\u6B64\u7C7B\u8D44\u4EA7\u6570\u636E\uFF0C\u65E0\u6CD5\u5BFC\u51FA");
                return;
            }

            task.setMessage("\u6B63\u5728\u89E3\u6790\u548C\u8131\u654F\u6570\u636E...");
            task.setProgress(30);
            task.setUpdatedAt(LocalDateTime.now());

            // Parse and flatten all records from snapshots
            List<Map<String, Object>> allRecords = snapshots.stream()
                    .flatMap(s -> parseRecords(s.getPrimaryPayload()).stream())
                    .collect(Collectors.toList());

            // Also add secondary payload records for account type
            if ("account".equals(assetType)) {
                List<Map<String, Object>> secondaryRecords = snapshots.stream()
                        .flatMap(s -> parseRecords(s.getSecondaryPayload()).stream())
                        .collect(Collectors.toList());
                allRecords.addAll(secondaryRecords);
            }

            if (allRecords.isEmpty()) {
                failTask(task, "\u8D44\u4EA7\u6570\u636E\u4E3A\u7A7A\uFF0C\u65E0\u6CD5\u5BFC\u51FA");
                return;
            }

            task.setTotalRecords(allRecords.size());

            // Count risk levels
            int highRisk = 0, mediumRisk = 0;
            for (Map<String, Object> record : allRecords) {
                String riskLevel = normalizeRiskLevelString(record.get("risk_level"));
                if ("high".equals(riskLevel)) {
                    highRisk++;
                } else if ("medium".equals(riskLevel)) {
                    mediumRisk++;
                }
            }
            task.setHighRiskCount(highRisk);
            task.setMediumRiskCount(mediumRisk);

            task.setMessage("\u6B63\u5728\u751F\u6210\u6E05\u5355\u6587\u4EF6...");
            task.setProgress(50);
            task.setUpdatedAt(LocalDateTime.now());

            // Apply masking
            List<Map<String, Object>> maskedRecords = allRecords.stream()
                    .map(ExportServiceImpl::maskRecord)
                    .collect(Collectors.toList());

            task.setProgress(70);
            task.setUpdatedAt(LocalDateTime.now());

            // Handle chunking for large datasets
            String fileContent;
            String fileName;
            if (maskedRecords.size() > CHUNK_SIZE) {
                int totalChunks = (int) Math.ceil((double) maskedRecords.size() / CHUNK_SIZE);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < totalChunks; i++) {
                    int start = i * CHUNK_SIZE;
                    int end = Math.min(start + CHUNK_SIZE, maskedRecords.size());
                    List<Map<String, Object>> chunk = maskedRecords.subList(start, end);

                    if (i > 0) {
                        sb.append("\n--- \u7B2C").append(i + 1).append("\u90E8\u5206 / \u5171").append(totalChunks).append("\u90E8\u5206 ---\n\n");
                    }
                    sb.append(generateContent(format, chunk, host, assetType));
                    task.setProgress(70 + (int) ((i + 1) * 25.0 / totalChunks));
                    task.setMessage("\u6B63\u5728\u751F\u6210\u6E05\u5355\uFF08\u5DF2\u5B8C\u6210" + (i + 1) + "/" + totalChunks + "\u90E8\u5206\uFF09");
                    task.setUpdatedAt(LocalDateTime.now());
                }
                fileContent = sb.toString();
            } else {
                fileContent = generateContent(format, maskedRecords, host, assetType);
                task.setProgress(95);
                task.setUpdatedAt(LocalDateTime.now());
            }

            // Compute MD5
            task.setMessage("\u6B63\u5728\u8BA1\u7B97\u6570\u636E\u5B8C\u6574\u6027\u6821\u9A8C\u503C...");
            task.setProgress(97);
            task.setUpdatedAt(LocalDateTime.now());

            byte[] contentBytes = fileContent.getBytes(StandardCharsets.UTF_8);
            String md5Hash = computeMd5(contentBytes);
            task.setMd5Hash(md5Hash);

            // Encode file data
            String base64Data = Base64.getEncoder().encodeToString(contentBytes);
            task.setFileData(base64Data);

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            String safeHostname = sanitizeFilename(host.getHostname());
            String ext = formatExtension(format);
            task.setFileName(safeHostname + "_" + assetType + "_" + timestamp + "." + ext);
            task.setContentType(contentType(format));

            task.setStatus("COMPLETED");
            task.setProgress(100);
            task.setMessage("\u5BFC\u51FA\u5B8C\u6210");
            task.setUpdatedAt(LocalDateTime.now());

        } catch (Exception e) {
            failTask(task, "\u5BFC\u51FA\u5931\u8D25: " + e.getMessage());
        }
    }

    // ---- Data parsing ----

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseRecords(String payload) {
        if (payload == null || payload.isBlank()) {
            return List.of();
        }
        try {
            JSONArray arr = JSON.parseArray(payload);
            if (arr == null) {
                return List.of();
            }
            return arr.stream()
                    .filter(item -> item instanceof JSONObject)
                    .map(item -> (Map<String, Object>) ((JSONObject) item).getInnerMap())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return List.of();
        }
    }

    // ---- Data masking ----

    private static Map<String, Object> maskRecord(Map<String, Object> record) {
        Map<String, Object> masked = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : record.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            masked.put(key, maskValue(key, value));
        }
        return masked;
    }

    private static Object maskValue(String fieldName, Object value) {
        if (value == null) {
            return null;
        }
        String strValue = String.valueOf(value);
        String lowerField = fieldName.toLowerCase(Locale.ROOT);

        // Mask IP addresses: show first 3 segments only
        if (isIpField(lowerField)) {
            return maskIpAddress(strValue);
        }

        // Mask sensitive fields: show hash-like truncated value
        if (isSensitiveField(lowerField)) {
            return maskSensitiveValue(strValue);
        }

        return value;
    }

    private static boolean isIpField(String lowerField) {
        for (String ipName : IP_FIELD_NAMES) {
            if (lowerField.contains(ipName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSensitiveField(String lowerField) {
        for (String sensitiveName : SENSITIVE_FIELD_NAMES) {
            if (lowerField.contains(sensitiveName)) {
                return true;
            }
        }
        return false;
    }

    static String maskIpAddress(String ip) {
        if (ip == null || ip.isBlank()) {
            return ip;
        }
        String trimmed = ip.trim();
        // Handle IPv4: 192.168.1.xxx
        String[] parts = trimmed.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + "." + parts[2] + ".xxx";
        }
        // Handle IPv6 or malformed: just show it as-is but mark
        if (trimmed.contains(":")) {
            String[] ipv6Parts = trimmed.split(":");
            if (ipv6Parts.length >= 4) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < Math.min(3, ipv6Parts.length); i++) {
                    if (i > 0) sb.append(":");
                    sb.append(ipv6Parts[i]);
                }
                sb.append(":****");
                return sb.toString();
            }
        }
        return trimmed;
    }

    static String maskSensitiveValue(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        if (value.length() <= 6) {
            return "******";
        }
        return value.substring(0, 6) + "***";
    }

    // ---- Content generation ----

    private String generateContent(String format, List<Map<String, Object>> records, Host host, String assetType) {
        return switch (format) {
            case "json" -> generateJson(records, host, assetType);
            case "markdown" -> generateMarkdown(records, host, assetType);
            default -> generateCsv(records, host, assetType);
        };
    }

    private String generateCsv(List<Map<String, Object>> records, Host host, String assetType) {
        if (records.isEmpty()) {
            return "";
        }

        // Build header from first record''s keys
        List<String> headers = new java.util.ArrayList<>(records.get(0).keySet());

        StringBuilder sb = new StringBuilder();
        // Metadata header
        sb.append("# ").append(getAssetLabel(assetType)).append("\u8D44\u4EA7\u6E05\u5355\n");
        sb.append("# \u4E3B\u673A: ").append(host.getHostname() != null ? host.getHostname() : "\u672A\u547D\u540D").append("\n");
        sb.append("# \u5BFC\u51FA\u65F6\u95F4: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
        sb.append("# \u8BB0\u5F55\u6570: ").append(records.size()).append("\n\n");

        // CSV header row
        sb.append(headers.stream().map(this::csvEscape).collect(Collectors.joining(","))).append("\n");

        // CSV data rows
        for (Map<String, Object> record : records) {
            sb.append(headers.stream()
                    .map(h -> csvEscape(String.valueOf(record.getOrDefault(h, ""))))
                    .collect(Collectors.joining(",")))
                    .append("\n");
        }
        return sb.toString();
    }

    private String generateJson(List<Map<String, Object>> records, Host host, String assetType) {
        JSONObject root = new JSONObject();
        root.put("title", getAssetLabel(assetType) + "\u8D44\u4EA7\u6E05\u5355");
        root.put("hostName", host.getHostname());
        root.put("hostIp", maskIpAddress(host.getIpAddress()));
        root.put("macAddress", host.getMacAddress());
        root.put("exportTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        root.put("totalRecords", records.size());
        root.put("assetType", assetType);
        root.put("records", records);
        return JSON.toJSONString(root, true);
    }

    private String generateMarkdown(List<Map<String, Object>> records, Host host, String assetType) {
        StringBuilder sb = new StringBuilder();
        String assetLabel = getAssetLabel(assetType);
        String hostName = host.getHostname() != null ? host.getHostname() : "\u672a\u547d\u540d";
        String exportTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        // ============ Title ============
        sb.append("# ").append(assetLabel).append("\u8d44\u4ea7\u6e05\u5355\n\n");

        // ============ Overview ============
        sb.append("## \u6982\u89c8\n\n");
        sb.append("| \u9879\u76ee | \u5185\u5bb9 |\n");
        sb.append("|------|------|\n");
        sb.append("| \u4e3b\u673a\u540d\u79f0 | ").append(hostName).append(" |\n");
        sb.append("| IP \u5730\u5740 | ").append(maskIpAddress(host.getIpAddress())).append(" |\n");
        sb.append("| MAC \u5730\u5740 | ").append(host.getMacAddress() != null ? host.getMacAddress() : "--").append(" |\n");
        sb.append("| \u8d44\u4ea7\u7c7b\u578b | ").append(assetLabel).append(" |\n");
        sb.append("| \u5bfc\u51fa\u65f6\u95f4 | ").append(exportTime).append(" |\n");
        sb.append("| \u8bb0\u5f55\u603b\u6570 | ").append(records.size()).append(" |\n");

        // Risk summary
        int highCount = 0, mediumCount = 0;
        for (Map<String, Object> r : records) {
            String rl = normalizeRiskLevelString(r.get("risk_level"));
            if ("high".equals(rl)) highCount++;
            else if ("medium".equals(rl)) mediumCount++;
        }
        if (highCount > 0 || mediumCount > 0) {
            sb.append("| \u98ce\u9669\u6982\u51b5 | ");
            if (highCount > 0) sb.append("\u26a0\ufe0f \u9ad8\u98ce\u9669 ").append(highCount).append(" \u9879");
            if (highCount > 0 && mediumCount > 0) sb.append("\uff0c");
            if (mediumCount > 0) sb.append("\u4e2d\u98ce\u9669 ").append(mediumCount).append(" \u9879");
            sb.append(" |\n");
        }
        sb.append("\n---\n\n");

        // ============ Summary Table ============
        sb.append("## \u8d44\u4ea7\u6c47\u603b\n\n");
        List<String> summaryKeys = getSummaryKeys(assetType, records);
        if (!summaryKeys.isEmpty() && !records.isEmpty()) {
            sb.append("| \u5e8f\u53f7 |");
            for (String key : summaryKeys) {
                sb.append(" ").append(translateFieldName(key, assetType)).append(" |");
            }
            sb.append(" \u98ce\u9669\u7b49\u7ea7 |\n");

            sb.append("|------|");
            for (int k = 0; k < summaryKeys.size(); k++) {
                sb.append("------|");
            }
            sb.append("------|\n");

            for (int i = 0; i < records.size(); i++) {
                Map<String, Object> record = records.get(i);
                sb.append("| ").append(i + 1).append(" |");
                for (String key : summaryKeys) {
                    String val = formatCellValue(record.get(key));
                    sb.append(" ").append(val).append(" |");
                }
                String rl = normalizeRiskLevelString(record.get("risk_level"));
                sb.append(" ").append(formatRiskBadge(rl)).append(" |\n");
            }
            sb.append("\n");
        }

        sb.append("---\n\n");

        // ============ Detailed Records ============
        sb.append("## \u8d44\u4ea7\u8be6\u60c5\n\n");

        for (int i = 0; i < records.size(); i++) {
            Map<String, Object> record = records.get(i);
            String rl = normalizeRiskLevelString(record.get("risk_level"));
            String riskBadge = formatRiskBadge(rl);

            sb.append("### ").append(i + 1).append(". ");
            String recordTitle = getRecordTitle(record, assetType);
            sb.append(recordTitle);
            if (!riskBadge.isEmpty()) {
                sb.append(" ").append(riskBadge);
            }
            sb.append("\n\n");

            List<FieldGroup> groups = groupRecordFields(record, assetType);
            for (FieldGroup group : groups) {
                if (group.fields.isEmpty()) continue;
                sb.append("**").append(group.label).append("**\n\n");
                sb.append("| \u5b57\u6bb5 | \u503c |\n");
                sb.append("|------|-----|\n");
                for (Map.Entry<String, Object> entry : group.fields) {
                    String fieldLabel = translateFieldName(entry.getKey(), assetType);
                    String value = formatCellValue(entry.getValue());
                    sb.append("| ").append(fieldLabel).append(" | ").append(mdEscape(value)).append(" |\n");
                }
                sb.append("\n");
            }
            sb.append("---\n\n");
        }

        sb.append("*\u7531\u5a01\u80c1\u611f\u77e5\u5e73\u53f0\u81ea\u52a8\u751f\u6210*\n");
        return sb.toString();
    }


    @Override
    public ExportTask startBatchExport(BatchExportRequest request, Long userId, boolean isAdmin) {
        String taskId = UUID.randomUUID().toString().replace("-", "");
        String normalizedFormat = normalizeFormat(request.getFormat());

        ExportTask task = new ExportTask();
        task.setTaskId(taskId);
        task.setStatus("PENDING");
        task.setProgress(0);
        task.setFormat(normalizedFormat);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        task.setMessage("正在准备批量导出...");

        TASK_STORE.put(taskId, task);
        executeBatchExport(task, request, normalizedFormat, isAdmin);
        return task;
    }

    @Async
    public void executeBatchExport(ExportTask task, BatchExportRequest request, String format, boolean isAdmin) {
        try {
            List<Long> hostIds = request.getHostIds();
            List<String> assetTypes = request.getAssetTypes();
            if (hostIds == null || hostIds.isEmpty()) { failTask(task, "请选择至少一台主机"); return; }
            if (assetTypes == null || assetTypes.isEmpty()) { failTask(task, "请选择至少一种资产类型"); return; }

            List<String> normalizedTypes = assetTypes.stream().map(this::normalizeAssetType).distinct().collect(Collectors.toList());
            int totalSteps = hostIds.size() * normalizedTypes.size();
            int step = 0;

            java.util.LinkedHashMap<String, java.util.LinkedHashMap<String, java.util.List<Map<String, Object>>>> report = new java.util.LinkedHashMap<>();
            int totalRecords = 0, highRisk = 0, mediumRisk = 0;

            for (Long hostId : hostIds) {
                Host host = hostMapper.selectById(hostId);
                if (host == null) continue;
                String hostLabel = host.getHostname() != null ? host.getHostname() : ("ID:" + hostId);
                java.util.LinkedHashMap<String, java.util.List<Map<String, Object>>> hostData = new java.util.LinkedHashMap<>();

                for (String at : normalizedTypes) {
                    step++;
                    task.setProgress(5 + (int)(step * 80.0 / Math.max(totalSteps, 1)));
                    task.setMessage("正在导出 " + hostLabel + " - " + getAssetLabel(at));
                    task.setUpdatedAt(LocalDateTime.now());

                    List<AssetSnapshotView> snapshots = assetSnapshotService.listByMacAddress(at, host.getMacAddress());
                    if (snapshots.isEmpty()) continue;

                    List<Map<String, Object>> records = snapshots.stream()
                            .flatMap(s -> parseRecords(s.getPrimaryPayload()).stream())
                            .collect(Collectors.toList());
                    if ("account".equals(at)) {
                        records.addAll(snapshots.stream()
                                .flatMap(s -> parseRecords(s.getSecondaryPayload()).stream())
                                .collect(Collectors.toList()));
                    }
                    if (records.isEmpty()) continue;

                    List<Map<String, Object>> masked = records.stream().map(ExportServiceImpl::maskRecord).collect(Collectors.toList());
                    totalRecords += masked.size();
                    for (Map<String, Object> r : masked) {
                        String rl = normalizeRiskLevelString(r.get("risk_level"));
                        if ("high".equals(rl)) highRisk++;
                        else if ("medium".equals(rl)) mediumRisk++;
                    }
                    hostData.put(at, masked);
                }
                if (!hostData.isEmpty()) report.put(hostLabel, hostData);
            }

            if (totalRecords == 0) { failTask(task, "所选主机暂无资产数据"); return; }

            task.setTotalRecords(totalRecords);
            task.setHighRiskCount(highRisk);
            task.setMediumRiskCount(mediumRisk);
            task.setProgress(88);
            task.setMessage("正在生成报告...");
            task.setUpdatedAt(LocalDateTime.now());

            String fileContent = generateBatchReport(format, report, normalizedTypes, hostIds.size());
            byte[] contentBytes = fileContent.getBytes(StandardCharsets.UTF_8);
            String base64Data = Base64.getEncoder().encodeToString(contentBytes);
            task.setFileData(base64Data);
            task.setMd5Hash(computeMd5(contentBytes));

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            String ext = formatExtension(format);
            task.setFileName("asset_inventory_" + timestamp + "." + ext);
            task.setContentType(contentType(format));
            task.setStatus("COMPLETED");
            task.setProgress(100);
            task.setMessage("导出完成");
            task.setUpdatedAt(LocalDateTime.now());
        } catch (Exception e) {
            failTask(task, "批量导出失败: " + e.getMessage());
        }
    }

    private String generateBatchReport(String format,
            java.util.LinkedHashMap<String, java.util.LinkedHashMap<String, java.util.List<Map<String, Object>>>> report,
            List<String> assetTypes, int hostCount) {
        return switch (format) {
            case "json" -> generateBatchJson(report, assetTypes);
            default -> generateBatchMarkdown(report, assetTypes, hostCount);
        };
    }

    private String generateBatchMarkdown(
            java.util.LinkedHashMap<String, java.util.LinkedHashMap<String, java.util.List<Map<String, Object>>>> report,
            List<String> assetTypes, int hostCount) {
        StringBuilder sb = new StringBuilder();
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        sb.append("# 资产探测清单报告\n\n");
        sb.append("| 项目 | 内容 |\n");
        sb.append("|------|------|\n");
        sb.append("| 导出时间 | ").append(now).append(" |\n");
        sb.append("| 主机数量 | ").append(hostCount).append(" |\n");
        sb.append("| 资产类型 | ").append(assetTypes.stream().map(this::getAssetLabel).collect(Collectors.joining("、"))).append(" |\n\n");
        sb.append("---\n\n");

        int hostIdx = 0;
        for (var entry : report.entrySet()) {
            hostIdx++;
            String hostLabel = entry.getKey();
            sb.append("## ").append(hostIdx).append(". ").append(hostLabel).append("\n\n");
            for (var typeEntry : entry.getValue().entrySet()) {
                String typeLabel = getAssetLabel(typeEntry.getKey());
                var records = typeEntry.getValue();
                sb.append("### ").append(typeLabel).append("（").append(records.size()).append(" 条）\n\n");
                if (records.isEmpty()) { sb.append("_暂无数据_\n\n"); continue; }
                var headers = new java.util.ArrayList<>(records.get(0).keySet());
                sb.append("| ");
                for (String h : headers) sb.append(translateFieldName(h)).append(" | ");
                sb.append("\n|");
                for (int i = 0; i < headers.size(); i++) sb.append("------|");
                sb.append("\n");
                for (var rec : records) {
                    sb.append("| ");
                    for (String h : headers) {
                        Object v = rec.get(h);
                        String vs = v != null ? String.valueOf(v) : "--";
                        if ("risk_level".equals(h)) {
                            if (vs.contains("高")) vs = "**\uD83D\uDD34 " + vs + "**";
                            else if (vs.contains("中")) vs = "*\uD83D\uDFE1 " + vs + "*";
                        }
                        sb.append(mdEscape(vs)).append(" | ");
                    }
                    sb.append("\n");
                }
                sb.append("\n");
            }
            sb.append("---\n\n");
        }
        sb.append("_本报告由威胁感知平台自动生成_\n");
        return sb.toString();
    }

    private String generateBatchJson(
            java.util.LinkedHashMap<String, java.util.LinkedHashMap<String, java.util.List<Map<String, Object>>>> report,
            List<String> assetTypes) {
        JSONObject root = new JSONObject();
        root.put("exportTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        root.put("assetTypes", assetTypes);
        root.put("hosts", report);
        return JSON.toJSONString(root, true);
    }

    private String translateFieldName(String field) {
        return switch (field) {
            case "name", "Name" -> "名称";
            case "enabled", "Enabled" -> "启用";
            case "full_name" -> "全名";
            case "description" -> "描述";
            case "sid" -> "SID";
            case "last_logon" -> "最后登录";
            case "is_shadow_account" -> "是否影子账号";
            case "risk_level" -> "风险等级";
            case "result" -> "分析结果";
            case "State", "state" -> "状态";
            case "StartMode", "start_mode" -> "启动模式";
            case "StartName", "start_name" -> "启动账户";
            case "DisplayName", "display_name" -> "显示名称";
            case "ProcessId", "process_id" -> "进程ID";
            case "ExecutablePath", "executable_path" -> "可执行路径";
            case "version", "Version" -> "版本";
            case "publisher", "Publisher" -> "发布者";
            case "install_date" -> "安装日期";
            case "CommandLine", "command_line" -> "命令行";
            default -> field;
        };
    }


    private String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String mdEscape(String value) {
        if (value == null) {
            return "--";
        }
        return value.replace("|", "\\|").replace("\n", " ");
    }

    private String computeMd5(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private void failTask(ExportTask task, String message) {
        task.setStatus("FAILED");
        task.setMessage(message);
        task.setUpdatedAt(LocalDateTime.now());
    }

    private String normalizeAssetType(String assetType) {
        if (assetType == null) {
            throw new BusinessException("\u8D44\u4EA7\u7C7B\u578B\u4E0D\u80FD\u4E3A\u7A7A");
        }
        String normalized = assetType.trim().toLowerCase(Locale.ROOT);
        if (!ASSET_LABEL_MAP.containsKey(normalized)) {
            throw new BusinessException("\u4E0D\u652F\u6301\u7684\u8D44\u4EA7\u7C7B\u578B: " + assetType);
        }
        return normalized;
    }

    private String normalizeFormat(String format) {
        if (format == null) {
            return "csv";
        }
        String normalized = format.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "json", "markdown", "md" -> normalized.equals("md") ? "markdown" : normalized;
            default -> "csv";
        };
    }

    private String formatExtension(String format) {
        return switch (format) {
            case "json" -> "json";
            case "markdown" -> "md";
            default -> "csv";
        };
    }

    private String contentType(String format) {
        return switch (format) {
            case "json" -> "application/json; charset=UTF-8";
            case "markdown" -> "text/markdown; charset=UTF-8";
            default -> "text/csv; charset=UTF-8";
        };
    }

    private String getAssetLabel(String assetType) {
        return ASSET_LABEL_MAP.getOrDefault(assetType, assetType);
    }

    private String sanitizeFilename(String name) {
        if (name == null) {
            return "unknown";
        }
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").replaceAll("\\s+", "_");
    }

    private String normalizeRiskLevelString(Object value) {
        if (value == null) {
            return "";
        }
        String raw = String.valueOf(value).trim();
        if (raw.contains("\u9AD8") || raw.equalsIgnoreCase("high")) {
            return "high";
        }
        if (raw.contains("\u4E2D") || raw.equalsIgnoreCase("medium")) {
            return "medium";
        }
        if (raw.contains("\u4F4E") || raw.equalsIgnoreCase("low")) {
            return "low";
        }
        return "safe";
    }

    // ---- Markdown export helpers ----

    private static class FieldGroup {
        final String label;
        final List<Map.Entry<String, Object>> fields = new java.util.ArrayList<>();

        FieldGroup(String label) {
            this.label = label;
        }
    }

    private List<String> getSummaryKeys(String assetType, List<Map<String, Object>> records) {
        if (records.isEmpty()) return java.util.Collections.emptyList();
        Map<String, Object> first = records.get(0);
        java.util.Set<String> allKeys = first.keySet();

        List<String> priorityKeys = new java.util.ArrayList<>();
        String[] candidates = switch (assetType) {
            case "account" -> new String[]{"username", "name", "type", "status", "enabled", "group_name"};
            case "service" -> new String[]{"name", "display_name", "status", "start_type", "port"};
            case "process" -> new String[]{"name", "pid", "ppid", "cpu_usage", "memory_usage", "status"};
            case "app" -> new String[]{"name", "version", "publisher", "install_date", "architecture"};
            default -> new String[]{"name", "type", "status"};
        };

        for (String key : candidates) {
            if (allKeys.contains(key)) {
                priorityKeys.add(key);
                if (priorityKeys.size() >= 4) break;
            }
        }
        return priorityKeys;
    }

    private String translateFieldName(String key, String assetType) {
        if (key == null) return "--";
        return switch (key) {
            case "id" -> "ID";
            case "name" -> "\u540d\u79f0";
            case "type" -> "\u7c7b\u578b";
            case "status" -> "\u72b6\u6001";
            case "description" -> "\u63cf\u8ff0";
            case "created_at" -> "\u521b\u5efa\u65f6\u95f4";
            case "updated_at" -> "\u66f4\u65b0\u65f6\u95f4";
            case "risk_level" -> "\u98ce\u9669\u7b49\u7ea7";
            case "risk_reason" -> "\u98ce\u9669\u539f\u56e0";
            case "risk_advice" -> "\u5efa\u8bae";
            case "username" -> "\u7528\u6237\u540d";
            case "full_name" -> "\u5168\u540d";
            case "group_name" -> "\u6240\u5c5e\u7ec4";
            case "enabled" -> "\u662f\u5426\u542f\u7528";
            case "password_required" -> "\u9700\u5bc6\u7801";
            case "last_login" -> "\u6700\u540e\u767b\u5f55";
            case "home_directory" -> "\u4e3b\u76ee\u5f55";
            case "shell" -> "Shell";
            case "uid" -> "UID";
            case "gid" -> "GID";
            case "display_name" -> "\u663e\u793a\u540d\u79f0";
            case "start_type" -> "\u542f\u52a8\u7c7b\u578b";
            case "service_type" -> "\u670d\u52a1\u7c7b\u578b";
            case "port" -> "\u7aef\u53e3";
            case "binary_path" -> "\u7a0b\u5e8f\u8def\u5f84";
            case "pid" -> "\u8fdb\u7a0bID";
            case "ppid" -> "\u7236\u8fdb\u7a0bID";
            case "cpu_usage" -> "CPU\u5360\u7528";
            case "memory_usage" -> "\u5185\u5b58\u5360\u7528";
            case "executable_path" -> "\u53ef\u6267\u884c\u6587\u4ef6";
            case "command_line" -> "\u547d\u4ee4\u884c";
            case "thread_count" -> "\u7ebf\u7a0b\u6570";
            case "handle_count" -> "\u53e5\u67c4\u6570";
            case "start_time" -> "\u542f\u52a8\u65f6\u95f4";
            case "user_name" -> "\u8fd0\u884c\u7528\u6237";
            case "version" -> "\u7248\u672c";
            case "publisher" -> "\u53d1\u5e03\u8005";
            case "install_date" -> "\u5b89\u88c5\u65e5\u671f";
            case "architecture" -> "\u67b6\u6784";
            case "install_location" -> "\u5b89\u88c5\u4f4d\u7f6e";
            case "uninstall_string" -> "\u5378\u8f7d\u547d\u4ee4";
            case "size_mb" -> "\u5927\u5c0f(MB)";
            default -> key;
        };
    }

    private String formatCellValue(Object value) {
        if (value == null) return "--";
        String s = String.valueOf(value).trim();
        if (s.isEmpty()) return "--";
        if (s.length() > 50) return s.substring(0, 47) + "...";
        return s;
    }

    private String formatRiskBadge(String riskLevel) {
        if (riskLevel == null || riskLevel.isEmpty()) return "";
        return switch (riskLevel) {
            case "high" -> "\u26a0\ufe0f \u9ad8\u98ce\u9669";
            case "medium" -> "\u26a0 \u4e2d\u98ce\u9669";
            case "low" -> "\u2705 \u4f4e\u98ce\u9669";
            default -> "\u2705 \u65e0\u98ce\u9669";
        };
    }

    private String getRecordTitle(Map<String, Object> record, String assetType) {
        String name = String.valueOf(record.getOrDefault("name", ""));
        String username = String.valueOf(record.getOrDefault("username", ""));
        String pid = String.valueOf(record.getOrDefault("pid", ""));

        if (!"null".equals(username) && !username.isEmpty()) return username;
        if (!"null".equals(name) && !name.isEmpty()) return name;
        if (!"null".equals(pid) && !pid.isEmpty()) return "PID: " + pid;
        return "\u8bb0\u5f55";
    }

    private List<FieldGroup> groupRecordFields(Map<String, Object> record, String assetType) {
        List<FieldGroup> groups = new java.util.ArrayList<>();

        FieldGroup identity = new FieldGroup("\ud83d\udccc \u57fa\u672c\u4fe1\u606f");
        FieldGroup security = new FieldGroup("\ud83d\udd12 \u5b89\u5168\u76f8\u5173");
        FieldGroup detail = new FieldGroup("\ud83d\udcca \u5176\u4ed6\u8be6\u60c5");

        java.util.Set<String> identityKeys = switch (assetType) {
            case "account" -> java.util.Set.of("username", "name", "full_name", "uid", "gid", "group_name", "home_directory", "shell");
            case "service" -> java.util.Set.of("name", "display_name", "status", "start_type", "service_type");
            case "process" -> java.util.Set.of("name", "pid", "ppid", "status", "user_name", "start_time");
            case "app" -> java.util.Set.of("name", "version", "publisher", "architecture", "install_date", "install_location");
            default -> java.util.Set.of("name", "type", "status");
        };

        java.util.Set<String> securityKeys = java.util.Set.of("risk_level", "risk_reason", "risk_advice", "enabled", "password_required");

        for (Map.Entry<String, Object> entry : record.entrySet()) {
            String key = entry.getKey();
            if (securityKeys.contains(key)) {
                security.fields.add(entry);
            } else if (identityKeys.contains(key)) {
                identity.fields.add(entry);
            } else {
                detail.fields.add(entry);
            }
        }

        groups.add(identity);
        groups.add(security);
        groups.add(detail);
        return groups;
    }

}

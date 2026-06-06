package com.cd.server.impl;

import com.cd.entity.AssetSnapshotView;
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

        sb.append("# ").append(assetLabel).append("\u8D44\u4EA7\u6E05\u5355\n\n");
        sb.append("| \u9879\u76EE | \u5185\u5BB9 |\n");
        sb.append("|--------|------|\n");
        sb.append("| \u4E3B\u673A\u540D\u79F0 | ").append(host.getHostname() != null ? host.getHostname() : "\u672A\u547D\u540D").append(" |\n");
        sb.append("| IP \u5730\u5740 | ").append(maskIpAddress(host.getIpAddress())).append(" |\n");
        sb.append("| MAC \u5730\u5740 | ").append(host.getMacAddress()).append(" |\n");
        sb.append("| \u8D44\u4EA7\u7C7B\u578B | ").append(assetLabel).append(" |\n");
        sb.append("| \u5BFC\u51FA\u65F6\u95F4 | ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append(" |\n");
        sb.append("| \u8BB0\u5F55\u603B\u6570 | ").append(records.size()).append(" |\n\n");

        sb.append("## \u8D44\u4EA7\u5217\u8868\n\n");

        for (int i = 0; i < records.size(); i++) {
            Map<String, Object> record = records.get(i);
            sb.append("### ").append(i + 1).append(". \u8BB0\u5F55\n\n");
            sb.append("| \u5B57\u6BB5 | \u503C |\n");
            sb.append("|------|-----|\n");
            for (Map.Entry<String, Object> entry : record.entrySet()) {
                String value = entry.getValue() != null ? String.valueOf(entry.getValue()) : "--";
                sb.append("| ").append(entry.getKey()).append(" | ").append(mdEscape(value)).append(" |\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    // ---- Helpers ----

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
}

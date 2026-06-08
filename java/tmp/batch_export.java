    
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
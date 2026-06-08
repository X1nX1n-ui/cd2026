var exportTaskId = null;
var exportPollTimer = null;

document.getElementById("batchExportBtn").addEventListener("click", function () {
    openBatchExportDialog();
});

function openBatchExportDialog() {
    var checked = table.checkStatus("hostTable");
    var selectedHosts = checked.data || [];
    if (selectedHosts.length === 0) {
        layer.msg("请先勾选要导出的主机");
        return;
    }
    var hostNames = selectedHosts.map(function (h) { return h.hostname || h.ipAddress || "ID:" + h.id; });
    var hostListHtml = hostNames.slice(0, 5).map(function (n) { return '<span style="display:inline-block;background:#f0f6ff;padding:2px 8px;border-radius:10px;margin:2px;font-size:12px;">' + PlatformUtils.escapeHtml(n) + '</span>'; }).join("");
    if (hostNames.length > 5) hostListHtml += ' <span style="color:#999;font-size:12px;">等 ' + hostNames.length + ' 台主机</span>';

    var html = '<div style="padding:12px 20px;">'
        + '<div style="margin-bottom:12px;"><strong>已选择主机：</strong></div>'
        + '<div style="margin-bottom:16px;">' + hostListHtml + '</div>'
        + '<div style="margin-bottom:12px;"><strong>选择资产类型：</strong></div>'
        + '<div style="display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-bottom:16px;">'
        + '<label class="schedule-option-item"><input type="checkbox" name="exp_account" checked><span class="schedule-option-label">账号</span></label>'
        + '<label class="schedule-option-item"><input type="checkbox" name="exp_service" checked><span class="schedule-option-label">服务</span></label>'
        + '<label class="schedule-option-item"><input type="checkbox" name="exp_process" checked><span class="schedule-option-label">进程</span></label>'
        + '<label class="schedule-option-item"><input type="checkbox" name="exp_app" checked><span class="schedule-option-label">安装程序</span></label>'
        + '</div>'
        + '<div style="margin-bottom:12px;"><strong>导出格式：</strong></div>'
        + '<div style="display:flex;gap:12px;margin-bottom:16px;">'
        + '<label><input type="radio" name="expFormat" value="markdown" checked> Markdown（推荐，清晰易读）</label>'
        + '<label><input type="radio" name="expFormat" value="json"> JSON</label>'
        + '</div>'
        + '<div id="exportProgressBar" style="display:none;margin-bottom:12px;">'
        + '<div style="background:#f0f0f0;border-radius:8px;height:8px;overflow:hidden;">'
        + '<div id="exportProgressFill" style="height:100%;background:var(--color-primary,#4f86f7);width:0%;transition:width 0.3s;"></div>'
        + '</div>'
        + '<div id="exportProgressText" style="font-size:12px;color:#666;margin-top:4px;"></div>'
        + '</div>'
        + '<div style="display:flex;justify-content:flex-end;gap:8px;">'
        + '<button type="button" class="layui-btn layui-btn-sm page-secondary-btn" id="exportCancelBtn">取消</button>'
        + '<button type="button" class="layui-btn layui-btn-sm page-btn" id="exportStartBtn">开始导出</button>'
        + '</div>'
        + '</div>';

    var dlgIdx = layer.open({
        type: 1,
        title: "导出资产清单",
        area: ["520px", "auto"],
        shadeClose: true,
        content: html,
        success: function (layero) {
            var root = layero[0] || layero;
            root.querySelector("#exportCancelBtn").addEventListener("click", function () { layer.close(dlgIdx); });
            root.querySelector("#exportStartBtn").addEventListener("click", function () {
                startBatchExport(root, dlgIdx, selectedHosts);
            });
        }
    });
}

function startBatchExport(root, dlgIdx, selectedHosts) {
    var types = [];
    ["account","service","process","app"].forEach(function (t) {
        if (root.querySelector('input[name="exp_' + t + '"]').checked) types.push(t);
    });
    if (types.length === 0) { layer.msg("请至少选择一种资产类型"); return; }
    var format = root.querySelector('input[name="expFormat"]:checked').value;
    var hostIds = selectedHosts.map(function (h) { return h.id; });

    var startBtn = root.querySelector("#exportStartBtn");
    var cancelBtn = root.querySelector("#exportCancelBtn");
    var bar = root.querySelector("#exportProgressBar");
    var fill = root.querySelector("#exportProgressFill");
    var text = root.querySelector("#exportProgressText");
    startBtn.disabled = true;
    cancelBtn.disabled = true;
    bar.style.display = "block";
    fill.style.width = "5%";
    text.textContent = "正在提交导出任务...";

    PlatformUtils.request("/api/assets/export/batch", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ hostIds: hostIds, assetTypes: types, format: format })
    }).then(function (task) {
        exportTaskId = task.taskId;
        pollBatchExport(task.taskId, fill, text, dlgIdx, root);
    }).catch(function (err) {
        bar.style.display = "none";
        startBtn.disabled = false;
        cancelBtn.disabled = false;
        layer.msg("启动导出失败：" + (err.message || ""));
    });
}

function pollBatchExport(taskId, fill, text, dlgIdx, root) {
    exportPollTimer = setInterval(function () {
        PlatformUtils.request("/api/assets/export/" + taskId).then(function (task) {
            fill.style.width = (task.progress || 0) + "%";
            text.textContent = task.message || "";
            if (task.status === "COMPLETED") {
                clearInterval(exportPollTimer);
                layer.close(dlgIdx);
                fetch("/api/assets/export/" + task.taskId + "/download", { headers: PlatformUtils.authHeaders() })
                    .then(function (r) { return r.blob(); })
                    .then(function (blob) {
                        var a = document.createElement("a");
                        a.href = URL.createObjectURL(blob);
                        a.download = task.fileName || "export";
                        document.body.appendChild(a);
                        a.click();
                        document.body.removeChild(a);
                        layer.msg("导出完成，共 " + task.totalRecords + " 条记录", { icon: 1 });
                    }).catch(function () { layer.msg("下载失败"); });
            } else if (task.status === "FAILED") {
                clearInterval(exportPollTimer);
                text.textContent = task.message || "导出失败";
                root.querySelector("#exportStartBtn").disabled = false;
                root.querySelector("#exportCancelBtn").disabled = false;
                layer.msg(task.message || "导出失败");
            }
        }).catch(function () {});
    }, 1000);
}

// Patch clearAssetExportPoll to also clear our timer
var _origClearExport = typeof clearAssetExportPoll === "function" ? clearAssetExportPoll : function(){};
clearAssetExportPoll = function() {
    _origClearExport();
    if (exportPollTimer) { clearInterval(exportPollTimer); exportPollTimer = null; }
};
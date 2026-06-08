with open(r"d:\3\code\java\src\main\resources\static\pages\assets\hosts.html", "r", encoding="utf-8") as f:
    content = f.read()

marker = "function configureAutoRefresh(value) {"

schedule_js = r"""

        // ===========================================
        //  探测策略调度配置
        // ===========================================
        var scheduleDialogIndex = null;
        var scheduleConfig = null;
        var scheduleHostCache = [];

        document.getElementById("openProbeScheduleBtn").addEventListener("click", function () {
            openProbeScheduleDialog();
        });

        function openProbeScheduleDialog() {
            Promise.all([
                PlatformUtils.request("/api/probe-schedule", { method: "GET" }),
                PlatformUtils.request("/api/hosts?status=1&pageSize=200", { method: "GET" })
            ]).then(function (results) {
                scheduleConfig = results[0];
                var hostPage = results[1];
                scheduleHostCache = hostPage.rows || hostPage.data || [];
                renderScheduleDialog();
            }).catch(function (error) {
                layer.msg("加载探测策略失败：" + (error.message || "未知错误"));
            });
        }

        function renderScheduleDialog() {
            var config = scheduleConfig;
            var enabled = config.enabled === true || config.enabled === 1;
            var cronExpr = config.cronExpression || "0 */5 * * * ?";
            var targetType = config.targetType || "all_online";
            var selectedIds = parseSelectedHostIds(config.targetHostIds);

            var html = '<div class="schedule-dialog-shell">'
                + '<div class="schedule-section">'
                + '<div class="schedule-section-title">策略状态</div>'
                + '<label class="schedule-toggle">'
                + '<input type="checkbox" id="scheduleEnabled" ' + (enabled ? 'checked' : '') + '>'
                + '<span class="schedule-toggle-track"></span>'
                + '<span class="schedule-toggle-label">' + (enabled ? '自动探测已开启' : '自动探测已关闭') + '</span>'
                + '</label>'
                + '</div>'
                + '<div class="schedule-section">'
                + '<div class="schedule-section-title">执行周期</div>'
                + '<div class="schedule-cron-presets" id="cronPresets">'
                + buildCronPreset("每5分钟", "0 */5 * * * ?", cronExpr)
                + buildCronPreset("每15分钟", "0 */15 * * * ?", cronExpr)
                + buildCronPreset("每30分钟", "0 */30 * * * ?", cronExpr)
                + buildCronPreset("每1小时", "0 0 */1 * * ?", cronExpr)
                + buildCronPreset("每2小时", "0 0 */2 * * ?", cronExpr)
                + buildCronPreset("每6小时", "0 0 */6 * * ?", cronExpr)
                + buildCronPreset("每12小时", "0 0 */12 * * ?", cronExpr)
                + buildCronPreset("自定义", "__custom__", cronExpr, true)
                + '</div>'
                + '<div class="schedule-cron-custom" id="cronCustomRow" style="display:' + (isCustomCron(cronExpr) ? 'flex' : 'none') + ';">'
                + '<input type="text" id="cronCustomInput" value="' + PlatformUtils.escapeHtml(cronExpr) + '" placeholder="Cron 表达式，如 0 */5 * * * ?">'
                + '</div>'
                + '<div class="schedule-cron-hint">格式：秒 分 时 日 月 周。当前 Cron：<code>' + PlatformUtils.escapeHtml(cronExpr) + '</code></div>'
                + '</div>'
                + '<div class="schedule-section">'
                + '<div class="schedule-section-title">目标主机</div>'
                + '<div style="margin-bottom:8px;">'
                + '<label style="margin-right:20px;cursor:pointer;"><input type="radio" name="targetType" value="all_online" ' + (targetType === "all_online" ? 'checked' : '') + '> 全部在线主机</label>'
                + '<label style="cursor:pointer;"><input type="radio" name="targetType" value="selected" ' + (targetType === "selected" ? 'checked' : '') + '> 指定主机</label>'
                + '</div>'
                + '<div class="schedule-host-chips" id="hostChips" style="display:' + (targetType === "selected" ? 'flex' : 'none') + ';">'
                + buildHostChips(selectedIds)
                + '</div>'
                + '</div>'
                + '<div class="schedule-section">'
                + '<div class="schedule-section-title">探测资产类型</div>'
                + '<div class="schedule-option-grid">'
                + buildAssetOption("account", "账号", "本机账号与影子账号", config.probeAccount)
                + buildAssetOption("service", "服务", "主机服务信息", config.probeService)
                + buildAssetOption("process", "进程", "运行进程", config.probeProcess)
                + buildAssetOption("app", "安装程序", "已安装软件", config.probeApp)
                + '</div>'
                + '</div>'
                + '<div class="schedule-dialog-footer">'
                + '<div class="left-actions">'
                + '<button type="button" class="layui-btn layui-btn-sm page-secondary-btn" id="executeNowBtn">立即执行一次</button>'
                + '</div>'
                + '<div>'
                + '<button type="button" class="layui-btn layui-btn-sm page-secondary-btn" id="scheduleCancelBtn">取消</button>'
                + '<button type="button" class="layui-btn layui-btn-sm page-btn" id="scheduleSaveBtn" style="margin-left:8px;">保存配置</button>'
                + '</div>'
                + '</div>'
                + '</div>';

            scheduleDialogIndex = layer.open({
                type: 1,
                title: "探测策略配置",
                area: ["600px", "auto"],
                shadeClose: true,
                content: html,
                success: function (layero) { bindScheduleDialogEvents(layero); },
                cancel: function () { scheduleDialogIndex = null; }
            });
        }

        function buildCronPreset(label, value, currentCron, isCustom) {
            var active = (!isCustom && value === currentCron) || (isCustom && isCustomCron(currentCron));
            return '<span class="schedule-cron-preset' + (active ? ' active' : '') + '" data-cron="' + PlatformUtils.escapeHtml(value) + '">' + label + '</span>';
        }

        function isCustomCron(cron) {
            var presets = ["0 */5 * * * ?", "0 */15 * * * ?", "0 */30 * * * ?", "0 0 */1 * * ?", "0 0 */2 * * ?", "0 0 */6 * * ?", "0 0 */12 * * ?"];
            return presets.indexOf(cron) === -1;
        }

        function buildHostChips(selectedIds) {
            var hosts = scheduleHostCache;
            if (!hosts.length) return '<span style="font-size:12px;color:#999;">暂无在线主机</span>';
            return hosts.map(function (h) {
                var sel = selectedIds.indexOf(h.id) >= 0;
                return '<span class="schedule-host-chip' + (sel ? ' selected' : '') + '" data-host-id="' + h.id + '">'
                    + '<span class="chip-dot"></span>'
                    + PlatformUtils.escapeHtml(h.hostname || h.ipAddress || h.macAddress)
                    + '</span>';
            }).join("");
        }

        function buildAssetOption(name, label, desc, checked) {
            var isChecked = checked === 1 || checked === true;
            return '<label class="schedule-option-item">'
                + '<input type="checkbox" name="probe_' + name + '" ' + (isChecked ? 'checked' : '') + '>'
                + '<span class="schedule-option-label">' + label + '</span>'
                + '<span class="schedule-option-desc">' + desc + '</span>'
                + '</label>';
        }

        function parseSelectedHostIds(idsStr) {
            if (!idsStr) return [];
            return idsStr.split(",").map(function (s) { return parseInt(s.trim(), 10); }).filter(function (n) { return !isNaN(n); });
        }

        function bindScheduleDialogEvents(layero) {
            var root = layero[0] || layero;
            root.querySelector("#scheduleEnabled").addEventListener("change", function () {
                this.parentNode.querySelector(".schedule-toggle-label").textContent = this.checked ? "自动探测已开启" : "自动探测已关闭";
            });
            root.querySelector("#cronPresets").addEventListener("click", function (e) {
                var preset = e.target.closest(".schedule-cron-preset");
                if (!preset) return;
                var cronValue = preset.getAttribute("data-cron");
                var customRow = root.querySelector("#cronCustomRow");
                var customInput = root.querySelector("#cronCustomInput");
                this.querySelectorAll(".schedule-cron-preset").forEach(function (p) { p.classList.remove("active"); });
                preset.classList.add("active");
                if (cronValue === "__custom__") {
                    customRow.style.display = "flex";
                    customInput.focus();
                } else {
                    customRow.style.display = "none";
                    customInput.value = cronValue;
                }
            });
            root.querySelector("#cronCustomInput").addEventListener("input", function () {
                root.querySelector("#cronPresets").querySelectorAll(".schedule-cron-preset").forEach(function (p) { p.classList.remove("active"); });
                var customPreset = root.querySelector('.schedule-cron-preset[data-cron="__custom__"]');
                if (customPreset) customPreset.classList.add("active");
            });
            root.querySelectorAll('input[name="targetType"]').forEach(function (radio) {
                radio.addEventListener("change", function () {
                    root.querySelector("#hostChips").style.display = this.value === "selected" ? "flex" : "none";
                });
            });
            root.querySelector("#hostChips").addEventListener("click", function (e) {
                var chip = e.target.closest(".schedule-host-chip");
                if (!chip) return;
                chip.classList.toggle("selected");
            });
            root.querySelector("#scheduleCancelBtn").addEventListener("click", function () {
                layer.close(scheduleDialogIndex);
                scheduleDialogIndex = null;
            });
            root.querySelector("#scheduleSaveBtn").addEventListener("click", function () {
                saveScheduleConfig(root);
            });
            root.querySelector("#executeNowBtn").addEventListener("click", function () {
                var btn = this;
                btn.disabled = true;
                btn.textContent = "发送中...";
                PlatformUtils.request("/api/probe-schedule/execute-now", { method: "POST" })
                    .then(function () { layer.msg("探测指令已发送", { icon: 1 }); })
                    .catch(function (error) { layer.msg("执行失败：" + (error.message || "")); })
                    .finally(function () { btn.disabled = false; btn.textContent = "立即执行一次"; });
            });
        }

        function saveScheduleConfig(root) {
            var enabled = root.querySelector("#scheduleEnabled").checked;
            var targetType = root.querySelector('input[name="targetType"]:checked').value;
            var cronExpr = root.querySelector("#cronCustomInput").value.trim();
            var selectedIds = [];
            if (targetType === "selected") {
                root.querySelectorAll(".schedule-host-chip.selected").forEach(function (chip) {
                    var id = parseInt(chip.getAttribute("data-host-id"), 10);
                    if (!isNaN(id)) selectedIds.push(id);
                });
            }
            var payload = {
                enabled: enabled,
                cronExpression: cronExpr || "0 */5 * * * ?",
                targetType: targetType,
                targetHostIds: targetType === "selected" ? selectedIds.join(",") : null,
                probeAccount: root.querySelector('input[name="probe_account"]').checked ? 1 : 0,
                probeService: root.querySelector('input[name="probe_service"]').checked ? 1 : 0,
                probeProcess: root.querySelector('input[name="probe_process"]').checked ? 1 : 0,
                probeApp: root.querySelector('input[name="probe_app"]').checked ? 1 : 0
            };
            if (!payload.probeAccount && !payload.probeService && !payload.probeProcess && !payload.probeApp) {
                layer.msg("请至少选择一种探测资产类型");
                return;
            }
            var saveBtn = root.querySelector("#scheduleSaveBtn");
            saveBtn.disabled = true;
            saveBtn.textContent = "保存中...";
            PlatformUtils.request("/api/probe-schedule", {
                method: "PUT",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(payload)
            }).then(function (saved) {
                scheduleConfig = saved;
                layer.close(scheduleDialogIndex);
                scheduleDialogIndex = null;
                layer.msg("探测策略已保存" + (saved.enabled ? "，自动探测已生效" : ""), { icon: 1 });
            }).catch(function (error) {
                layer.msg("保存失败：" + (error.message || ""));
            }).finally(function () {
                saveBtn.disabled = false;
                saveBtn.textContent = "保存配置";
            });
        }
"""

# Insert before marker
content = content.replace(marker, schedule_js + "\n\n        " + marker)

with open(r"d:\3\code\java\src\main\resources\static\pages\assets\hosts.html", "w", encoding="utf-8") as f:
    f.write(content)

print("JavaScript injected successfully")
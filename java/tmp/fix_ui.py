with open(r"d:\3\code\java\src\main\resources\static\pages\assets\hosts.html", "rb") as f:
    data = f.read()

rn = b"\r\n"
changes = 0

# 1. Replace cronToHuman with a smarter version
old_func = b"""        function cronToHuman(cron) {
            var map = {
                "0 */5 * * * ?": "每5分钟自动探测一次",
                "0 */15 * * * ?": "每15分钟自动探测一次",
                "0 */30 * * * ?": "每30分钟自动探测一次",
                "0 0 */1 * * ?": "每1小时自动探测一次",
                "0 0 */2 * * ?": "每2小时自动探测一次",
                "0 0 */6 * * ?": "每6小时自动探测一次",
                "0 0 */12 * * ?": "每12小时自动探测一次"
            };
            return map[cron] || "自定义 Cron 表达式";
        }"""

new_func = b"""        function cronToHuman(cron) {
            if (!cron) return "未设置";
            var parts = cron.trim().split(/\\s+/);
            if (parts.length < 6) return cron;
            var sec = parts[0], min = parts[1], hour = parts[2], day = parts[3], mon = parts[4], dow = parts[5];
            // Every N minutes: 0 */N * * * ?
            if (sec === "0" && min.startsWith("*/") && hour === "*" && day === "*" && mon === "*") {
                return "每" + min.slice(2) + "分钟自动探测一次";
            }
            // Every N hours: 0 0 */N * * ?
            if (sec === "0" && min === "0" && hour.startsWith("*/") && day === "*" && mon === "*") {
                return "每" + hour.slice(2) + "小时自动探测一次";
            }
            // Daily at specific time: 0 M H * * ?
            if (sec === "0" && !min.includes("/") && !min.includes(",") && !hour.includes("/") && !hour.includes(",") && day === "*" && mon === "*") {
                return "每天 " + hour + ":" + (min.length === 1 ? "0" + min : min) + " 自动探测一次";
            }
            // Every N days: 0 M H */N * ?
            if (sec === "0" && day.startsWith("*/") && mon === "*") {
                return "每" + day.slice(2) + "天 " + hour + ":" + (min.length === 1 ? "0" + min : min) + " 自动探测一次";
            }
            // Fallback: show readable parts
            var desc = [];
            if (min.startsWith("*/")) desc.push("每" + min.slice(2) + "分钟");
            else if (min !== "*" && min !== "0") desc.push("第" + min + "分");
            if (hour.startsWith("*/")) desc.push("每" + hour.slice(2) + "小时");
            else if (hour !== "*") desc.push(hour + "时");
            if (day.startsWith("*/")) desc.push("每" + day.slice(2) + "天");
            if (desc.length > 0) return desc.join("") + "自动探测一次";
            return cron;
        }"""

if old_func in data:
    data = data.replace(old_func, new_func)
    changes += 1
    print("1. cronToHuman upgraded")
else:
    print("1. cronToHuman NOT found")

# 2. Fix dialog footer button alignment - add min-width to buttons
old_footer = b"""<div class="schedule-dialog-footer">'
                + '<div class="left-actions">'
                + '<button type="button" class="layui-btn layui-btn-sm page-secondary-btn" id="executeNowBtn">立即执行一次</button>'
                + '</div>'
                + '<div>'
                + '<button type="button" class="layui-btn layui-btn-sm page-secondary-btn" id="scheduleCancelBtn">取消</button>'
                + '<button type="button" class="layui-btn layui-btn-sm page-btn" id="scheduleSaveBtn" style="margin-left:8px;">保存配置</button>'
                + '</div>'
                + '</div>'"""

new_footer = b"""<div class="schedule-dialog-footer">'
                + '<div class="left-actions">'
                + '<button type="button" class="layui-btn layui-btn-sm page-secondary-btn" id="executeNowBtn" style="min-width:110px;">立即执行一次</button>'
                + '</div>'
                + '<div style="display:flex;gap:8px;">'
                + '<button type="button" class="layui-btn layui-btn-sm page-secondary-btn" id="scheduleCancelBtn" style="min-width:80px;">取消</button>'
                + '<button type="button" class="layui-btn layui-btn-sm page-btn" id="scheduleSaveBtn" style="min-width:100px;">保存配置</button>'
                + '</div>'
                + '</div>'"""

if old_footer in data:
    data = data.replace(old_footer, new_footer)
    changes += 1
    print("2. Button alignment fixed")
else:
    print("2. Footer NOT found - checking...")
    idx = data.find(b'schedule-dialog-footer')
    if idx >= 0:
        print(data[idx:idx+400].decode('utf-8'))

print(f"Changes: {changes}")
with open(r"d:\3\code\java\src\main\resources\static\pages\assets\hosts.html", "wb") as f:
    f.write(data)
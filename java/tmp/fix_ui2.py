with open(r"d:\3\code\java\src\main\resources\static\pages\assets\hosts.html", "rb") as f:
    data = f.read()

rn = b"\r\n"
changes = 0

# 1. Smarter cronToHuman
old_func = (
    b"        function cronToHuman(cron) {" + rn +
    b"            var map = {" + rn +
    b'                "0 */5 * * * ?": "每5分钟自动探测一次",' + rn +
    b'                "0 */15 * * * ?": "每15分钟自动探测一次",' + rn +
    b'                "0 */30 * * * ?": "每30分钟自动探测一次",' + rn +
    b'                "0 0 */1 * * ?": "每1小时自动探测一次",' + rn +
    b'                "0 0 */2 * * ?": "每2小时自动探测一次",' + rn +
    b'                "0 0 */6 * * ?": "每6小时自动探测一次",' + rn +
    b'                "0 0 */12 * * ?": "每12小时自动探测一次"' + rn +
    b"            };" + rn +
    b'            return map[cron] || "自定义 Cron 表达式";' + rn +
    b"        }"
)

new_func = (
    b"        function cronToHuman(cron) {" + rn +
    b"            if (!cron) return \"未设置\";" + rn +
    b"            var parts = cron.trim().split(/\\s+/);" + rn +
    b"            if (parts.length < 6) return cron;" + rn +
    b"            var sec = parts[0], min = parts[1], hour = parts[2], day = parts[3], mon = parts[4];" + rn +
    b"            if (sec === \"0\" && min.startsWith(\"*/\") && hour === \"*\" && day === \"*\" && mon === \"*\") {" + rn +
    b"                return \"每\" + min.slice(2) + \"分钟自动探测一次\";" + rn +
    b"            }" + rn +
    b"            if (sec === \"0\" && min === \"0\" && hour.startsWith(\"*/\") && day === \"*\" && mon === \"*\") {" + rn +
    b"                return \"每\" + hour.slice(2) + \"小时自动探测一次\";" + rn +
    b"            }" + rn +
    b"            if (sec === \"0\" && !min.includes(\"/\") && !min.includes(\",\") && !hour.includes(\"/\") && !hour.includes(\",\") && day === \"*\" && mon === \"*\") {" + rn +
    b"                return \"每天 \" + hour + \":\" + (min.length === 1 ? \"0\" + min : min) + \" 自动探测一次\";" + rn +
    b"            }" + rn +
    b"            if (sec === \"0\" && day.startsWith(\"*/\") && mon === \"*\") {" + rn +
    b"                return \"每\" + day.slice(2) + \"天 \" + hour + \":\" + (min.length === 1 ? \"0\" + min : min) + \" 自动探测一次\";" + rn +
    b"            }" + rn +
    b"            var desc = [];" + rn +
    b"            if (min.startsWith(\"*/\")) desc.push(\"每\" + min.slice(2) + \"分钟\");" + rn +
    b"            else if (min !== \"*\" && min !== \"0\") desc.push(\"第\" + min + \"分\");" + rn +
    b"            if (hour.startsWith(\"*/\")) desc.push(\"每\" + hour.slice(2) + \"小时\");" + rn +
    b"            else if (hour !== \"*\") desc.push(hour + \"时\");" + rn +
    b"            if (day.startsWith(\"*/\")) desc.push(\"每\" + day.slice(2) + \"天\");" + rn +
    b"            if (desc.length > 0) return desc.join(\"\") + \"自动探测一次\";" + rn +
    b"            return cron;" + rn +
    b"        }"
)

if old_func in data:
    data = data.replace(old_func, new_func)
    changes += 1
    print("1. cronToHuman upgraded")
else:
    print("1. NOT FOUND")

# 2. Fix footer buttons
old_footer = (
    b"<div class=\"schedule-dialog-footer\">'" + rn +
    b"                + '<div class=\"left-actions\">'" + rn +
    b"                + '<button type=\"button\" class=\"layui-btn layui-btn-sm page-secondary-btn\" id=\"executeNowBtn\">立即执行一次</button>'" + rn +
    b"                + '</div>'" + rn +
    b"                + '<div>'" + rn +
    b"                + '<button type=\"button\" class=\"layui-btn layui-btn-sm page-secondary-btn\" id=\"scheduleCancelBtn\">取消</button>'" + rn +
    b"                + '<button type=\"button\" class=\"layui-btn layui-btn-sm page-btn\" id=\"scheduleSaveBtn\" style=\"margin-left:8px;\">保存配置</button>'" + rn +
    b"                + '</div>'" + rn +
    b"                + '</div>'"
)

new_footer = (
    b"<div class=\"schedule-dialog-footer\">'" + rn +
    b"                + '<div class=\"left-actions\">'" + rn +
    b"                + '<button type=\"button\" class=\"layui-btn layui-btn-sm page-secondary-btn\" id=\"executeNowBtn\" style=\"min-width:110px;\">立即执行一次</button>'" + rn +
    b"                + '</div>'" + rn +
    b"                + '<div style=\"display:flex;gap:8px;\">'" + rn +
    b"                + '<button type=\"button\" class=\"layui-btn layui-btn-sm page-secondary-btn\" id=\"scheduleCancelBtn\" style=\"min-width:80px;\">取消</button>'" + rn +
    b"                + '<button type=\"button\" class=\"layui-btn layui-btn-sm page-btn\" id=\"scheduleSaveBtn\" style=\"min-width:100px;\">保存配置</button>'" + rn +
    b"                + '</div>'" + rn +
    b"                + '</div>'"
)

if old_footer in data:
    data = data.replace(old_footer, new_footer)
    changes += 1
    print("2. Footer buttons fixed")
else:
    print("2. Footer NOT found")

print(f"Changes: {changes}")
with open(r"d:\3\code\java\src\main\resources\static\pages\assets\hosts.html", "wb") as f:
    f.write(data)
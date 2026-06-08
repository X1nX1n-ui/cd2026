with open(r"d:\3\code\java\src\main\resources\static\pages\assets\hosts.html", "rb") as f:
    data = f.read()

rn = b"\r\n"
C = lambda s: s.encode("utf-8")
changes = 0

# === 1. Replace cronToHuman ===
old_cron = (
    b"function cronToHuman(cron) {" + rn +
    b"            var map = {" + rn +
    b'                "0 */5 * * * ?": ' + C('"每5分钟自动探测一次",') + rn +
    b'                "0 */15 * * * ?": ' + C('"每15分钟自动探测一次",') + rn +
    b'                "0 */30 * * * ?": ' + C('"每30分钟自动探测一次",') + rn +
    b'                "0 0 */1 * * ?": ' + C('"每1小时自动探测一次",') + rn +
    b'                "0 0 */2 * * ?": ' + C('"每2小时自动探测一次",') + rn +
    b'                "0 0 */6 * * ?": ' + C('"每6小时自动探测一次",') + rn +
    b'                "0 0 */12 * * ?": ' + C('"每12小时自动探测一次"') + rn +
    b"            };" + rn +
    b"            return map[cron] || " + C('"自定义 Cron 表达式";') + rn +
    b"        }"
)

new_cron = (
    b"function cronToHuman(cron) {" + rn +
    b"            if (!cron) return " + C('"未设置";') + rn +
    b"            var parts = cron.trim().split(/\\s+/);" + rn +
    b"            if (parts.length < 6) return cron;" + rn +
    b"            var sec = parts[0], min = parts[1], hour = parts[2], day = parts[3], mon = parts[4];" + rn +
    b'            if (sec === "0" && min.startsWith("*/") && hour === "*" && day === "*" && mon === "*") {' + rn +
    b"                return " + C('"每" + min.slice(2) + "分钟自动探测一次";') + rn +
    b"            }" + rn +
    b'            if (sec === "0" && min === "0" && hour.startsWith("*/") && day === "*" && mon === "*") {' + rn +
    b"                return " + C('"每" + hour.slice(2) + "小时自动探测一次";') + rn +
    b"            }" + rn +
    b'            if (sec === "0" && !min.includes("/") && !min.includes(",") && !hour.includes("/") && !hour.includes(",") && day === "*" && mon === "*") {' + rn +
    b"                return " + C('"每天 " + hour + ":" + (min.length === 1 ? "0" + min : min) + " 自动探测一次";') + rn +
    b"            }" + rn +
    b'            if (sec === "0" && day.startsWith("*/") && mon === "*") {' + rn +
    b"                return " + C('"每" + day.slice(2) + "天 " + hour + ":" + (min.length === 1 ? "0" + min : min) + " 自动探测一次";') + rn +
    b"            }" + rn +
    b"            var desc = [];" + rn +
    b'            if (min.startsWith("*/")) desc.push(' + C('"每" + min.slice(2) + "分钟");') + rn +
    b'            else if (min !== "*" && min !== "0") desc.push(' + C('"第" + min + "分");') + rn +
    b'            if (hour.startsWith("*/")) desc.push(' + C('"每" + hour.slice(2) + "小时");') + rn +
    b"            else if (hour !== \"*\") desc.push(hour + " + C('"时");') + rn +
    b'            if (day.startsWith("*/")) desc.push(' + C('"每" + day.slice(2) + "天");') + rn +
    b'            if (desc.length > 0) return desc.join("") + ' + C('"自动探测一次";') + rn +
    b"            return cron;" + rn +
    b"        }"
)

if old_cron in data:
    data = data.replace(old_cron, new_cron)
    changes += 1
    print("1. cronToHuman upgraded")
else:
    print("1. cronToHuman NOT found")

# === 2. Footer buttons - add min-width ===
old_footer = (
    b"<div class=\"schedule-dialog-footer\">'" + rn +
    b"                + '<div class=\"left-actions\">'" + rn +
    b"                + '<button type=\"button\" class=\"layui-btn layui-btn-sm page-secondary-btn\" id=\"executeNowBtn\">" + C("立即执行一次") + b"</button>'" + rn +
    b"                + '</div>'" + rn +
    b"                + '<div>'" + rn +
    b"                + '<button type=\"button\" class=\"layui-btn layui-btn-sm page-secondary-btn\" id=\"scheduleCancelBtn\">" + C("取消") + b"</button>'" + rn +
    b"                + '<button type=\"button\" class=\"layui-btn layui-btn-sm page-btn\" id=\"scheduleSaveBtn\" style=\"margin-left:8px;\">" + C("保存配置") + b"</button>'" + rn +
    b"                + '</div>'" + rn +
    b"                + '</div>'"
)

new_footer = (
    b"<div class=\"schedule-dialog-footer\">'" + rn +
    b"                + '<div class=\"left-actions\">'" + rn +
    b"                + '<button type=\"button\" class=\"layui-btn layui-btn-sm page-secondary-btn\" id=\"executeNowBtn\" style=\"min-width:110px;\">" + C("立即执行一次") + b"</button>'" + rn +
    b"                + '</div>'" + rn +
    b"                + '<div style=\"display:flex;gap:8px;\">'" + rn +
    b"                + '<button type=\"button\" class=\"layui-btn layui-btn-sm page-secondary-btn\" id=\"scheduleCancelBtn\" style=\"min-width:80px;\">" + C("取消") + b"</button>'" + rn +
    b"                + '<button type=\"button\" class=\"layui-btn layui-btn-sm page-btn\" id=\"scheduleSaveBtn\" style=\"min-width:100px;\">" + C("保存配置") + b"</button>'" + rn +
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
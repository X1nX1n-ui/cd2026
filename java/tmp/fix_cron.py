with open(r"d:\3\code\java\src\main\resources\static\pages\assets\hosts.html", "rb") as f:
    data = f.read()

C = lambda s: s.encode("utf-8")

# Exact old bytes from the file (LF line endings, no trailing commas)
old = (
    b"function cronToHuman(cron) {\n"
    b"            var map = {\n"
    b'                "0 */5 * * * ?": ' + C('"每5分钟自动探测一次",') + b'\n'
    b'                "0 */15 * * * ?": ' + C('"每15分钟自动探测一次",') + b'\n'
    b'                "0 */30 * * * ?": ' + C('"每30分钟自动探测一次",') + b'\n'
    b'                "0 0 */1 * * ?": ' + C('"每1小时自动探测一次",') + b'\n'
    b'                "0 0 */2 * * ?": ' + C('"每2小时自动探测一次",') + b'\n'
    b'                "0 0 */6 * * ?": ' + C('"每6小时自动探测一次",') + b'\n'
    b'                "0 0 */12 * * ?": ' + C('"每12小时自动探测一次"') + b'\n'
    b"            };\n"
    b"            return map[cron] || " + C('"自定义 Cron 表达式";') + b'\n'
    b"        }"
)

new = (
    b"function cronToHuman(cron) {\n"
    b"            if (!cron) return " + C('"未设置";') + b'\n'
    b"            var parts = cron.trim().split(/\\s+/);\n"
    b"            if (parts.length < 6) return cron;\n"
    b"            var sec = parts[0], min = parts[1], hour = parts[2], day = parts[3], mon = parts[4];\n"
    b'            if (sec === "0" && min.startsWith("*/") && hour === "*" && day === "*" && mon === "*") {\n'
    b"                return " + C('"每" + min.slice(2) + "分钟自动探测一次";') + b'\n'
    b"            }\n"
    b'            if (sec === "0" && min === "0" && hour.startsWith("*/") && day === "*" && mon === "*") {\n'
    b"                return " + C('"每" + hour.slice(2) + "小时自动探测一次";') + b'\n'
    b"            }\n"
    b'            if (sec === "0" && !min.includes("/") && !min.includes(",") && !hour.includes("/") && !hour.includes(",") && day === "*" && mon === "*") {\n'
    b"                return " + C('"每天 " + hour + ":" + (min.length === 1 ? "0" + min : min) + " 自动探测一次";') + b'\n'
    b"            }\n"
    b'            if (sec === "0" && day.startsWith("*/") && mon === "*") {\n'
    b"                return " + C('"每" + day.slice(2) + "天 " + hour + ":" + (min.length === 1 ? "0" + min : min) + " 自动探测一次";') + b'\n'
    b"            }\n"
    b"            var desc = [];\n"
    b'            if (min.startsWith("*/")) desc.push(' + C('"每" + min.slice(2) + "分钟");') + b'\n'
    b'            else if (min !== "*" && min !== "0") desc.push(' + C('"第" + min + "分");') + b'\n'
    b'            if (hour.startsWith("*/")) desc.push(' + C('"每" + hour.slice(2) + "小时");') + b'\n'
    b"            else if (hour !== \"*\") desc.push(hour + " + C('"时");') + b'\n'
    b'            if (day.startsWith("*/")) desc.push(' + C('"每" + day.slice(2) + "天");') + b'\n'
    b'            if (desc.length > 0) return desc.join("") + ' + C('"自动探测一次";') + b'\n'
    b"            return cron;\n"
    b"        }"
)

if old in data:
    data = data.replace(old, new)
    print("cronToHuman upgraded!")
else:
    print("NOT FOUND - checking bytes:")
    idx = data.find(b"function cronToHuman")
    chunk = data[idx:idx+len(old)]
    if chunk == old:
        print("  Bytes match but replacement failed somehow")
    else:
        # Find where they differ
        for i, (a, b) in enumerate(zip(chunk, old)):
            if a != b:
                print(f"  First diff at byte {i}: file={hex(a)}, expected={hex(b)}")
                print(f"  Context: ...{chunk[max(0,i-20):i+20]}...")
                break

with open(r"d:\3\code\java\src\main\resources\static\pages\assets\hosts.html", "wb") as f:
    f.write(data)
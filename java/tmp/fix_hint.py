with open(r"d:\3\code\java\src\main\resources\static\pages\assets\hosts.html", "rb") as f:
    data = f.read()

# Replace the hint line
old_hint = '格式：秒 分 时 日 月 周。当前 Cron：<code>\' + PlatformUtils.escapeHtml(cronExpr) + \'</code>'.encode('utf-8')
new_hint = '<strong>\' + cronToHuman(cronExpr) + \'</strong> &nbsp;<span style="font-size:11px;color:#999;">(Cron: \' + PlatformUtils.escapeHtml(cronExpr) + \')</span>'.encode('utf-8')

if old_hint in data:
    data = data.replace(old_hint, new_hint)
    print("Hint text replaced")
else:
    print("Hint text NOT found")
    # Try to find it
    idx = data.find('schedule-cron-hint'.encode('utf-8'))
    if idx >= 0:
        snippet = data[idx:idx+200]
        print(f"Found: {snippet.decode('utf-8')}")

# Also add the cronToHuman function before buildCronPreset
old_func = 'function buildCronPreset'.encode('utf-8')
cron_to_human = '''
        function cronToHuman(cron) {
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
        }

        function buildCronPreset'''.encode('utf-8')

if old_func in data:
    data = data.replace(old_func, cron_to_human)
    print("cronToHuman function added")
else:
    print("buildCronPreset NOT found")

with open(r"d:\3\code\java\src\main\resources\static\pages\assets\hosts.html", "wb") as f:
    f.write(data)
print("hosts.html saved")
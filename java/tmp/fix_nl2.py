with open(r"d:\3\code\java\src\main\resources\static\pages\assets\hosts.html", "rb") as f:
    data = f.read()

C = lambda s: s.encode("utf-8")
rn = b"\r\n"
changes = 0

# === 1. Replace custom cron input row ===
# Build old and new as pure bytes
old_input = (
    b'<div class="schedule-cron-custom" id="cronCustomRow" style="display:\' + (isCustomCron(cronExpr) ? \'flex\' : \'none\') + \';">\'' + rn +
    b"                + '<input type=\"text\" id=\"cronCustomInput\" value=\"' + PlatformUtils.escapeHtml(cronExpr) + '\" placeholder=\"" + C("Cron 表达式，如 0 */5 * * * ?") + b"\">'" + rn +
    b"                + '</div>'"
)

new_input = (
    b'<div class="schedule-cron-custom" id="cronCustomRow" style="display:\' + (isCustomCron(cronExpr) ? \'flex\' : \'none\') + \';">\'' + rn +
    b"                + '<div style=\"flex:1;display:flex;flex-direction:column;gap:6px;\">'" + rn +
    b"                + '<input type=\"text\" id=\"cronHumanInput\" value=\"' + PlatformUtils.escapeHtml(cronToHuman(cronExpr)) + '\" placeholder=\"" + C("例如：每10分钟 / 每3小时 / 每天8:30") + b"\">'" + rn +
    b"                + '<input type=\"text\" id=\"cronRawInput\" value=\"' + PlatformUtils.escapeHtml(cronExpr) + '\" placeholder=\"Cron\" style=\"display:none;font-family:monospace;font-size:12px;color:#888;\">'" + rn +
    b"                + '<a href=\"#\" id=\"cronAdvancedToggle\" style=\"font-size:11px;color:#999;text-decoration:none;\">" + C("高级模式") + b"</a>'" + rn +
    b"                + '</div>'" + rn +
    b"                + '</div>'"
)

if old_input in data:
    data = data.replace(old_input, new_input)
    changes += 1
    print("1. Natural language input added")
else:
    print("1. NOT FOUND")

# === 2. Add humanToCron function ===
old_func = b"function cronToHuman(cron) {"
human_to_cron = (
    b"function humanToCron(text) {\n"
    b"            if (!text) return '';\n"
    b"            var t = text.trim();\n"
    b"            if (/^[0-9*\\/,?\\-]+(\\s+[0-9*\\/,?\\-]+){4,5}$/.test(t)) return t;\n"
    b"            var cn = {" + C('"一":1,"二":2,"三":3,"四":4,"五":5,"六":6,"七":7,"八":8,"九":9,"十":10,"零":0,"两":2,"半":30') + b"};\n"
    b"            t = t.replace(/[一二三四五六七八九十两零半]+/g, function(m) { return cn[m] || m; });\n"
    b"            var nums = t.match(/\\d+/g);\n"
    b"            if (!nums) return '';\n"
    b"            if (/分|min/i.test(t) && !/小时|时(?!间)|点|天|日/i.test(t)) {\n"
    b"                return '0 */' + nums[0] + ' * * * ?';\n"
    b"            }\n"
    b"            if (/小时|时(?!间)/i.test(t) && !/天|日/i.test(t)) {\n"
    b"                var mm = nums.length > 1 ? nums[1] : '0';\n"
    b"                return '0 ' + mm + ' */' + nums[0] + ' * * ?';\n"
    b"            }\n"
    b"            if (/天|日/i.test(t)) {\n"
    b"                var intv = '1';\n"
    b"                var m1 = t.match(/每.*?(\\d+).*?天/);\n"
    b"                if (m1) intv = m1[1];\n"
    b"                var hh = nums.length > 1 ? nums[nums.length-2] : '0';\n"
    b"                var mm2 = nums.length > 2 ? nums[nums.length-1] : '0';\n"
    b"                if (intv === '1') return '0 ' + mm2 + ' ' + hh + ' * * ?';\n"
    b"                return '0 ' + mm2 + ' ' + hh + ' */' + intv + ' * ?';\n"
    b"            }\n"
    b"            if (/:/.test(t) && nums.length >= 2) {\n"
    b"                var parts = t.split(':');\n"
    b"                return '0 ' + parts[1].replace(/\\D/g,'') + ' ' + parts[0].replace(/\\D/g,'') + ' * * ?';\n"
    b"            }\n"
    b"            return '';\n"
    b"        }\n"
    b"\n"
    b"        function cronToHuman(cron) {"
)

if old_func in data:
    data = data.replace(old_func, human_to_cron)
    changes += 1
    print("2. humanToCron added")
else:
    print("2. NOT FOUND")

# === 3. Preset click updates human input ===
old_preset = b"updateScheduleHint(root, cronValue);"
new_preset = (
    b"updateScheduleHint(root, cronValue);\n"
    b"                    var hi = root.querySelector('#cronHumanInput');\n"
    b"                    if (hi) hi.value = cronToHuman(cronValue);"
)
if old_preset in data:
    data = data.replace(old_preset, new_preset)
    changes += 1
    print("3. Preset updates human input")
else:
    print("3. NOT FOUND")

# === 4. Save parses human input ===
old_save = b'var cronExpr = root.querySelector("#cronCustomInput").value.trim();'
new_save = (
    b'var hi2 = root.querySelector("#cronHumanInput");\n'
    b'            var ri = root.querySelector("#cronRawInput");\n'
    b'            var ht = hi2 ? hi2.value.trim() : "";\n'
    b'            var rc = ri ? ri.value.trim() : "";\n'
    b"            var cronExpr = rc || humanToCron(ht) || ht;"
)
if old_save in data:
    data = data.replace(old_save, new_save)
    changes += 1
    print("4. Save parses human input")
else:
    print("4. NOT FOUND")

# === 5. Event handlers ===
old_evt = (
    b'root.querySelector("#cronCustomInput").addEventListener("input", function () {' + rn +
    b'                root.querySelector("#cronPresets").querySelectorAll(".schedule-cron-preset").forEach(function (p) { p.classList.remove("active"); });' + rn +
    b"                var customPreset = root.querySelector('.schedule-cron-preset[data-cron=\"__custom__\"]');" + rn +
    b'                if (customPreset) customPreset.classList.add("active");' + rn +
    b'                updateScheduleHint(root, this.value.trim());' + rn +
    b'            });'
)

new_evt = (
    b'var hi3 = root.querySelector("#cronHumanInput");' + rn +
    b'            var ri2 = root.querySelector("#cronRawInput");' + rn +
    b'            var at = root.querySelector("#cronAdvancedToggle");' + rn +
    b'            hi3.addEventListener("input", function () {' + rn +
    b'                root.querySelector("#cronPresets").querySelectorAll(".schedule-cron-preset").forEach(function (p) { p.classList.remove("active"); });' + rn +
    b"                var cp2 = root.querySelector('.schedule-cron-preset[data-cron=\"__custom__\"]');" + rn +
    b'                if (cp2) cp2.classList.add("active");' + rn +
    b'                var p = humanToCron(this.value.trim());' + rn +
    b'                if (p) { ri2.value = p; updateScheduleHint(root, p); }' + rn +
    b'            });' + rn +
    b'            ri2.addEventListener("input", function () {' + rn +
    b'                updateScheduleHint(root, this.value.trim());' + rn +
    b'            });' + rn +
    b'            at.addEventListener("click", function (e) {' + rn +
    b'                e.preventDefault();' + rn +
    b"                var rv = ri2.style.display !== 'none';" + rn +
    b"                ri2.style.display = rv ? 'none' : 'block';" + rn +
    b"                hi3.style.display = rv ? 'block' : 'none';" + rn +
    b"                this.textContent = rv ? " + C("'高级模式'") + b" : " + C("'简单模式'") + b";" + rn +
    b'            });'
)

if old_evt in data:
    data = data.replace(old_evt, new_evt)
    changes += 1
    print("5. Event handlers updated")
else:
    print("5. NOT FOUND")

print(f"Total changes: {changes}")
with open(r"d:\3\code\java\src\main\resources\static\pages\assets\hosts.html", "wb") as f:
    f.write(data)
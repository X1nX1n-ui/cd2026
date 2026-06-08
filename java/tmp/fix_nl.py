with open(r"d:\3\code\java\src\main\resources\static\pages\assets\hosts.html", "rb") as f:
    data = f.read()

C = lambda s: s.encode("utf-8")
rn = b"\r\n"
changes = 0

# === 1. Replace custom cron input row with natural language input ===
old_input = (
    b'<div class="schedule-cron-custom" id="cronCustomRow" style="display:\' + (isCustomCron(cronExpr) ? \'flex\' : \'none\') + \';">\'' + rn +
    b"                + '<input type=\"text\" id=\"cronCustomInput\" value=\"' + PlatformUtils.escapeHtml(cronExpr) + '\" placeholder=\"Cron 表达式，如 0 */5 * * * ?\">'" + rn +
    b"                + '</div>'"
)

new_input = (
    b'<div class="schedule-cron-custom" id="cronCustomRow" style="display:\' + (isCustomCron(cronExpr) ? \'flex\' : \'none\') + \';">\'' + rn +
    b"                + '<div style=\"flex:1;display:flex;flex-direction:column;gap:6px;\">'" + rn +
    b"                + '<input type=\"text\" id=\"cronHumanInput\" value=\"' + PlatformUtils.escapeHtml(cronToHuman(cronExpr)) + '\" placeholder=\"' + C('例如：每10分钟 / 每3小时 / 每天8:30 / 每2天 9:00') + b'\">'" + rn +
    b"                + '<input type=\"text\" id=\"cronRawInput\" value=\"' + PlatformUtils.escapeHtml(cronExpr) + '\" placeholder=\"Cron 表达式\" style=\"display:none;font-family:monospace;font-size:12px;color:#888;\">'" + rn +
    b"                + '<a href=\"#\" id=\"cronAdvancedToggle\" style=\"font-size:11px;color:#999;text-decoration:none;\">' + C('高级模式') + b'</a>'" + rn +
    b"                + '</div>'" + rn +
    b"                + '</div>'"
)

if old_input in data:
    data = data.replace(old_input, new_input)
    changes += 1
    print("1. Input replaced with natural language version")
else:
    print("1. Input NOT found")

# === 2. Add humanToCron function before cronToHuman ===
old_func = b"function cronToHuman(cron) {"
human_to_cron = (
    b"function humanToCron(text) {" + b"\n"
    b"            if (!text) return '';" + b"\n"
    b"            var t = text.trim();" + b"\n"
    b"            // Already a cron expression" + b"\n"
    b"            if (/^[0-9*/,?\\-]+(\\s+[0-9*/,?\\-]+){4,5}$/.test(t)) return t;" + b"\n"
    b"            // Normalize Chinese numbers" + b"\n"
    b"            var cn = {" + C('"一":1,"二":2,"三":3,"四":4,"五":5,"六":6,"七":7,"八":8,"九":9,"十":10,'
    b'"零":0,"两":2,"半":30') + b"};" + b"\n"
    b"            t = t.replace(/[一二三四五六七八九十两零半]+/g, function(m) { return cn[m] || m; });" + b"\n"
    b"            // Extract numbers: first number = interval, second = hour, third = minute" + b"\n"
    b"            var nums = t.match(/\\d+/g);" + b"\n"
    b"            if (!nums) return '';" + b"\n"
    b"            // 每X分钟 / X分钟一次" + b"\n"
    b"            if (/分|min/i.test(t) && !/小时|时(?!间)|点|天|日/i.test(t)) {" + b"\n"
    b"                return '0 */' + nums[0] + ' * * * ?';" + b"\n"
    b"            }" + b"\n"
    b"            // 每X小时 / X小时一次 (with optional :MM)" + b"\n"
    b"            if (/小时|时(?!间)/i.test(t) && !/天|日/i.test(t)) {" + b"\n"
    b"                var mm = nums.length > 1 ? nums[1] : '0';" + b"\n"
    b"                return '0 ' + mm + ' */' + nums[0] + ' * * ?';" + b"\n"
    b"            }" + b"\n"
    b"            // 每X天 [H:MM] / 每天 [H:MM]" + b"\n"
    b"            if (/天|日/i.test(t)) {" + b"\n"
    b"                var interval = /每.*?(\\d+).*?天/.test(t) ? t.match(/每.*?(\\d+).*?天/)[1] : '1';" + b"\n"
    b"                var hh = nums.length > 1 ? nums[nums.length-2] : '0';" + b"\n"
    b"                var mm2 = nums.length > 2 ? nums[nums.length-1] : '0';" + b"\n"
    b"                // If hour/minute look like time (H:MM format)" + b"\n"
    b"                if (/:/.test(t) && nums.length >= 2) {" + b"\n"
    b"                    hh = nums[nums.length-2]; mm2 = nums[nums.length-1];" + b"\n"
    b"                }" + b"\n"
    b"                if (interval === '1') return '0 ' + mm2 + ' ' + hh + ' * * ?';" + b"\n"
    b"                return '0 ' + mm2 + ' ' + hh + ' */' + interval + ' * ?';" + b"\n"
    b"            }" + b"\n"
    b"            // 每天H:MM / H:MM" + b"\n"
    b"            if (/每.*?天|每天/.test(t) || /^\\d{1,2}:\\d{2}/.test(t)) {" + b"\n"
    b"                var parts = t.split(':');" + b"\n"
    b"                if (parts.length >= 2) {" + b"\n"
    b"                    return '0 ' + parts[1].replace(/\\D/g,'') + ' ' + parts[0].replace(/\\D/g,'') + ' * * ?';" + b"\n"
    b"                }" + b"\n"
    b"                if (nums.length >= 2) return '0 ' + nums[1] + ' ' + nums[0] + ' * * ?';" + b"\n"
    b"            }" + b"\n"
    b"            return '';" + b"\n"
    b"        }" + b"\n"
    b"\n"
    b"        function cronToHuman(cron) {"
)

if old_func in data:
    data = data.replace(old_func, human_to_cron)
    changes += 1
    print("2. humanToCron added before cronToHuman")
else:
    print("2. cronToHuman NOT found")

# === 3. Update cron preset click handler to update human input ===
old_preset_update = b"updateScheduleHint(root, cronValue);"
new_preset_update = (
    b"updateScheduleHint(root, cronValue);" + b"\n"
    b"                    var humanInput = root.querySelector('#cronHumanInput');" + b"\n"
    b"                    if (humanInput) humanInput.value = cronToHuman(cronValue);"
)
if old_preset_update in data:
    data = data.replace(old_preset_update, new_preset_update)
    changes += 1
    print("3. Preset handler updates human input")
else:
    print("3. Preset handler NOT found")

# === 4. Update saveScheduleConfig to parse human input ===
old_save = b"var cronExpr = root.querySelector(\"#cronCustomInput\").value.trim();"
new_save = (
    b"var humanInput = root.querySelector(\"#cronHumanInput\");" + b"\n"
    b"            var rawInput = root.querySelector(\"#cronRawInput\");" + b"\n"
    b"            var humanText = humanInput ? humanInput.value.trim() : '';" + b"\n"
    b"            var rawCron = rawInput ? rawInput.value.trim() : '';" + b"\n"
    b"            var cronExpr = rawCron || humanToCron(humanText) || humanText;"
)

if old_save in data:
    data = data.replace(old_save, new_save)
    changes += 1
    print("4. Save uses humanToCron parser")
else:
    print("4. Save NOT found")

# === 5. Add advanced toggle and human input events in bindScheduleDialogEvents ===
# Find the input listener for cronCustomInput and replace with new ones
old_event = (
    b'root.querySelector("#cronCustomInput").addEventListener("input", function () {' + rn +
    b"                root.querySelector(\"#cronPresets\").querySelectorAll(\".schedule-cron-preset\").forEach(function (p) { p.classList.remove(\"active\"); });" + rn +
    b"                var customPreset = root.querySelector('.schedule-cron-preset[data-cron=\"__custom__\"]');" + rn +
    b"                if (customPreset) customPreset.classList.add(\"active\");" + rn +
    b"                updateScheduleHint(root, this.value.trim());" + rn +
    b"            });"
)

new_event = (
    b'var humanInputEl = root.querySelector("#cronHumanInput");' + rn +
    b"            var rawInputEl = root.querySelector(\"#cronRawInput\");" + rn +
    b'            var advToggle = root.querySelector("#cronAdvancedToggle");' + rn +
    b"            humanInputEl.addEventListener(\"input\", function () {" + rn +
    b"                root.querySelector(\"#cronPresets\").querySelectorAll(\".schedule-cron-preset\").forEach(function (p) { p.classList.remove(\"active\"); });" + rn +
    b"                var customPreset = root.querySelector('.schedule-cron-preset[data-cron=\"__custom__\"]');" + rn +
    b"                if (customPreset) customPreset.classList.add(\"active\");" + rn +
    b"                var parsed = humanToCron(this.value.trim());" + rn +
    b"                if (parsed) {" + rn +
    b"                    rawInputEl.value = parsed;" + rn +
    b"                    updateScheduleHint(root, parsed);" + rn +
    b"                }" + rn +
    b"            });" + rn +
    b"            rawInputEl.addEventListener(\"input\", function () {" + rn +
    b"                updateScheduleHint(root, this.value.trim());" + rn +
    b"            });" + rn +
    b"            advToggle.addEventListener(\"click\", function (e) {" + rn +
    b"                e.preventDefault();" + rn +
    b"                var rawVisible = rawInputEl.style.display !== 'none';" + rn +
    b"                rawInputEl.style.display = rawVisible ? 'none' : 'block';" + rn +
    b"                humanInputEl.style.display = rawVisible ? 'block' : 'none';" + rn +
    b"                this.textContent = rawVisible ? " + C("'高级模式'") + b" : " + C("'简单模式'") + b";" + rn +
    b"            });"
)

if old_event in data:
    data = data.replace(old_event, new_event)
    changes += 1
    print("5. Event handlers updated")
else:
    print("5. Event handlers NOT found")

print(f"Total changes: {changes}")
with open(r"d:\3\code\java\src\main\resources\static\pages\assets\hosts.html", "wb") as f:
    f.write(data)
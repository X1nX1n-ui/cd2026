with open(r"d:\3\code\java\src\main\resources\static\pages\assets\hosts.html", "rb") as f:
    data = f.read()

rn = b'\r\n'
changes = 0

# 1. Input listener
old1 = b'cronCustomInput").addEventListener("input", function () {' + rn + b'                root.querySelector("#cronPresets").querySelectorAll(".schedule-cron-preset").forEach(function (p) { p.classList.remove("active"); });' + rn + b'                var customPreset = root.querySelector(\'.schedule-cron-preset[data-cron="__custom__"]\');' + rn + b'                if (customPreset) customPreset.classList.add("active");' + rn + b'            });'
new1 = b'cronCustomInput").addEventListener("input", function () {' + rn + b'                root.querySelector("#cronPresets").querySelectorAll(".schedule-cron-preset").forEach(function (p) { p.classList.remove("active"); });' + rn + b'                var customPreset = root.querySelector(\'.schedule-cron-preset[data-cron="__custom__"]\');' + rn + b'                if (customPreset) customPreset.classList.add("active");' + rn + b'                updateScheduleHint(root, this.value.trim());' + rn + b'            });'

if old1 in data:
    data = data.replace(old1, new1)
    changes += 1
    print("1. OK")
else:
    print("1. FAIL")

# 2. Preset handler
old2 = b'if (cronValue === "__custom__") {' + rn + b'                    customRow.style.display = "flex";' + rn + b'                    customInput.focus();' + rn + b'                } else {' + rn + b'                    customRow.style.display = "none";' + rn + b'                    customInput.value = cronValue;' + rn + b'                }'
new2 = b'if (cronValue === "__custom__") {' + rn + b'                    customRow.style.display = "flex";' + rn + b'                    customInput.focus();' + rn + b'                } else {' + rn + b'                    customRow.style.display = "none";' + rn + b'                    customInput.value = cronValue;' + rn + b'                    updateScheduleHint(root, cronValue);' + rn + b'                }'

if old2 in data:
    data = data.replace(old2, new2)
    changes += 1
    print("2. OK")
else:
    print("2. FAIL")

print(f"Changes: {changes}")
with open(r"d:\3\code\java\src\main\resources\static\pages\assets\hosts.html", "wb") as f:
    f.write(data)
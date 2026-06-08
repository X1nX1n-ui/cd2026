with open(r"d:\3\code\java\src\main\resources\static\pages\assets\hosts.html", "rb") as f:
    data = f.read()

changes = 0

# 1. Input listener - exact from file
old1 = b'cronCustomInput").addEventListener("input", function () {\n                root.querySelector("#cronPresets").querySelectorAll(".schedule-cron-preset").forEach(function (p) { p.classList.remove("active"); });\n                var customPreset = root.querySelector(\'.schedule-cron-preset[data-cron="__custom__"]\');\n                if (customPreset) customPreset.classList.add("active");\n            });'
new1 = b'cronCustomInput").addEventListener("input", function () {\n                root.querySelector("#cronPresets").querySelectorAll(".schedule-cron-preset").forEach(function (p) { p.classList.remove("active"); });\n                var customPreset = root.querySelector(\'.schedule-cron-preset[data-cron="__custom__"]\');\n                if (customPreset) customPreset.classList.add("active");\n                updateScheduleHint(root, this.value.trim());\n            });'

if old1 in data:
    data = data.replace(old1, new1)
    changes += 1
    print("1. Input listener updated")
else:
    print("1. NOT FOUND")

# 2. Preset handler - exact from file
old2 = b'if (cronValue === "__custom__") {\n                    customRow.style.display = "flex";\n                    customInput.focus();\n                } else {\n                    customRow.style.display = "none";\n                    customInput.value = cronValue;\n                }'
new2 = b'if (cronValue === "__custom__") {\n                    customRow.style.display = "flex";\n                    customInput.focus();\n                } else {\n                    customRow.style.display = "none";\n                    customInput.value = cronValue;\n                    updateScheduleHint(root, cronValue);\n                }'

if old2 in data:
    data = data.replace(old2, new2)
    changes += 1
    print("2. Preset handler updated")
else:
    print("2. NOT FOUND")

print(f"Changes: {changes}")
with open(r"d:\3\code\java\src\main\resources\static\pages\assets\hosts.html", "wb") as f:
    f.write(data)
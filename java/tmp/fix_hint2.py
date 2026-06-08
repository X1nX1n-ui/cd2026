with open(r"d:\3\code\java\src\main\resources\static\pages\assets\hosts.html", "rb") as f:
    data = f.read()

changes = 0

# 1. Update cron input listener to also update hint
old1 = b"""root.querySelector("#cronCustomInput").addEventListener("input", function () {
                root.querySelector("#cronPresets").querySelectorAll(".schedule-cron-preset").forEach(function (p) { p.classList.remove("active"); });
                var customPreset = root.querySelector('.schedule-cron-preset[data-cron="__custom__"]');
                if (customPreset) customPreset.classList.add("active");
            });"""

new1 = b"""root.querySelector("#cronCustomInput").addEventListener("input", function () {
                root.querySelector("#cronPresets").querySelectorAll(".schedule-cron-preset").forEach(function (p) { p.classList.remove("active"); });
                var customPreset = root.querySelector('.schedule-cron-preset[data-cron="__custom__"]');
                if (customPreset) customPreset.classList.add("active");
                updateScheduleHint(root, this.value.trim());
            });"""

if old1 in data:
    data = data.replace(old1, new1)
    changes += 1
    print("1. Input listener: hint update added")
else:
    print("1. Input listener NOT found")

# 2. Update preset click handler
old2 = b"""if (cronValue === "__custom__") {
                    customRow.style.display = "flex";
                    customInput.focus();
                } else {
                    customRow.style.display = "none";
                    customInput.value = cronValue;
                }"""

new2 = b"""if (cronValue === "__custom__") {
                    customRow.style.display = "flex";
                    customInput.focus();
                } else {
                    customRow.style.display = "none";
                    customInput.value = cronValue;
                    updateScheduleHint(root, cronValue);
                }"""

if old2 in data:
    data = data.replace(old2, new2)
    changes += 1
    print("2. Preset click handler: hint update added")
else:
    print("2. Preset handler NOT found")

# 3. Add updateScheduleHint function
old3 = b'function saveScheduleConfig(root) {'
new3 = b"""function updateScheduleHint(root, cronValue) {
            var hintEl = root.querySelector(".schedule-cron-hint");
            if (hintEl) {
                hintEl.innerHTML = "<strong>" + cronToHuman(cronValue) + "</strong> <span style=\"font-size:11px;color:#999;\">(Cron: " + cronValue + ")</span>";
            }
        }

        function saveScheduleConfig(root) {"""

if old3 in data:
    data = data.replace(old3, new3)
    changes += 1
    print("3. updateScheduleHint function added")
else:
    print("3. saveScheduleConfig NOT found")

print(f"\nTotal changes: {changes}")

with open(r"d:\3\code\java\src\main\resources\static\pages\assets\hosts.html", "wb") as f:
    f.write(data)
print("Saved")
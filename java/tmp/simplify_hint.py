with open(r"d:\3\code\java\src\main\resources\static\pages\assets\hosts.html", "rb") as f:
    data = f.read()

rn = b'\r\n'
changes = 0

# Fix 1: Render hint - just show cronToHuman result
old1 = (
    b"<strong>' + cronToHuman(cronExpr) + '</strong> &nbsp;"
    b"<span style=\"font-size:11px;color:#999;\">(Cron: ' + PlatformUtils.escapeHtml(cronExpr) + ')</span>"
)
new1 = b"<strong>' + cronToHuman(cronExpr) + '</strong>"

if old1 in data:
    data = data.replace(old1, new1)
    changes += 1
    print("1. Render hint OK")
else:
    print("1. Render hint NOT FOUND")

# Fix 2: updateScheduleHint
old2 = (
    b'"<strong>" + cronToHuman(cronValue) + "</strong> '
    b'<span style=\\"font-size:11px;color:#999;\\">(Cron: " + cronValue + ")</span>"'
)
new2 = b'"<strong>" + cronToHuman(cronValue) + "</strong>"'

if old2 in data:
    data = data.replace(old2, new2)
    changes += 1
    print("2. updateScheduleHint OK")
else:
    print("2. updateScheduleHint NOT FOUND")
    idx = data.find(b'updateScheduleHint')
    if idx >= 0:
        print(repr(data[idx:idx+250]))

print(f"Changes: {changes}")
with open(r"d:\3\code\java\src\main\resources\static\pages\assets\hosts.html", "wb") as f:
    f.write(data)
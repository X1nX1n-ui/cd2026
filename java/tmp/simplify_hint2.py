with open(r"d:\3\code\java\src\main\resources\static\pages\assets\hosts.html", "rb") as f:
    data = f.read()

old = b'"<strong>" + cronToHuman(cronValue) + "</strong> <span style=\"font-size:11px;color:#999;\">(Cron: " + cronValue + ")</span>"'
new = b'"<strong>" + cronToHuman(cronValue) + "</strong>"'

if old in data:
    data = data.replace(old, new)
    print("updateScheduleHint simplified")
else:
    print("NOT FOUND")

with open(r"d:\3\code\java\src\main\resources\static\pages\assets\hosts.html", "wb") as f:
    f.write(data)
import shutil

src = r"d:\3\code\java\src\main\resources\static\pages\assets\hosts.html"
dst = r"d:\3\code\java\target\classes\static\pages\assets\hosts.html"
shutil.copy2(src, dst)

# Verify target
with open(dst, "rb") as f:
    data = f.read()
idx = data.find(b'openProbeScheduleBtn')
if idx >= 0:
    tag_end = data.find(b'>', idx) + 1
    closing = data.find(b'</button>', tag_end)
    text = data[tag_end:closing]
    print(f"Target button text: {text.decode('utf-8')}")
    print("Sync OK")
else:
    print("Button not found in target")
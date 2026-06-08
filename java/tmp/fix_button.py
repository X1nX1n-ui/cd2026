import os

filepath = r"d:\3\code\java\src\main\resources\static\pages\assets\hosts.html"

with open(filepath, "rb") as f:
    data = f.read()

# The correct UTF-8 bytes for "探测策略"
tan_ce_ce_lue = b"\xe6\x8e\xa2\xe6\xb5\x8b\xe7\xad\x96\xe7\x95\xa5"

# Find the button and replace ???? with correct Chinese
old_pattern = b'openProbeScheduleBtn" style="margin-left:8px;">????</button>'
new_pattern = b'openProbeScheduleBtn" style="margin-left:8px;">' + tan_ce_ce_lue + b'</button>'

count = data.count(old_pattern)
print(f"Found {count} occurrences of broken button")

if count > 0:
    data = data.replace(old_pattern, new_pattern)
    with open(filepath, "wb") as f:
        f.write(data)
    print("Fixed!")
    
    # Verify
    with open(filepath, "rb") as f:
        check = f.read()
    idx = check.find(b'openProbeScheduleBtn')
    tag_end = check.find(b'>', idx) + 1
    closing = check.find(b'</button>', tag_end)
    text = check[tag_end:closing]
    print(f"Button text now: {text.hex()} = {text.decode('utf-8')}")
else:
    print("Pattern not found, checking what's there...")
    idx = data.find(b'openProbeScheduleBtn')
    if idx >= 0:
        print(data[idx:idx+120].hex())
import re

with open(r"D:\3\code\java\src\main\resources\static\pages\assets\hosts.html", "r", encoding="utf-8", errors="replace") as f:
    text = f.read()

# All \ufffd patterns need fixing. Let me find them all
import re
# Find all occurrences of \ufffd with context
for m in re.finditer(r'[\u4e00-\u9fff]{0,10}\ufffd[^<]{0,20}', text):
    ctx = m.group()
    print("GARBLED:", repr(ctx))

# Count total
count = text.count('\ufffd')
print("\nTotal replacement chars:", count)

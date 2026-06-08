import re

with open(r"D:\3\code\java\src\main\resources\static\pages\assets\hosts.html", "r", encoding="utf-8", errors="replace") as f:
    text = f.read()

# Find all garbled patterns and write to file
with open(r"D:\3\code\java\tmp_garbled.txt", "w", encoding="utf-8") as out:
    for m in re.finditer(r'.{0,15}\ufffd.{0,15}', text):
        ctx = m.group()
        out.write(repr(ctx) + "\n")

    out.write("\nTotal replacement chars: %d\n" % text.count("\ufffd"))

print("Wrote garbled patterns to tmp_garbled.txt")

with open(r"D:\3\code\java\src\main\resources\static\pages\assets\hosts.html", "r", encoding="utf-8") as f:
    text = f.read()

# Line 1647 is completely broken. Replace the whole function's confirm call.
old = '\u5bfc\u51fa\u5931\u8d25\uff1a" + " + (task.message || ""\u672a\u77e5\u9519\u8bef") + "\u3002<br>\u8981\u91cd\u65b0\u5c1d\u8bd5\u5bfc\u51fa\u5417\uff1f",'
new = '\u5bfc\u51fa\u5931\u8d25\uff1a" + (task.message || "\u672a\u77e5\u9519\u8bef") + "\u3002<br>\u8981\u91cd\u65b0\u5c1d\u8bd5\u5bfc\u51fa\u5417\uff1f",'

if old in text:
    text = text.replace(old, new)
    print("Fixed!")
else:
    print("Pattern not found. Searching...")
    idx = text.find('\u5bfc\u51fa\u5931\u8d25')
    if idx >= 0:
        print("Context:", repr(text[idx:idx+150]))

# Also fix the title line
old2 = '{ title: "\u5bfc\u51fa\u5931\u8d25", btn: ["\u91cd\u8bd5", "\u53d6\u6d88"] },'
if old2 in text:
    print("Title line OK")
else:
    print("Title line BROKEN")

with open(r"D:\3\code\java\src\main\resources\static\pages\assets\hosts.html", "w", encoding="utf-8") as f:
    f.write(text)

# Verify quotes
import re
m = re.search(r"<script>\s*\n\s*layui\.use", text)
js = text[m.start():text.find("</script>", m.start())]
in_s = in_d = False
esc = False
for c in js:
    if esc: esc = False; continue
    if c == '\\': esc = True; continue
    if c == "'" and not in_d: in_s = not in_s
    elif c == '"' and not in_s: in_d = not in_d
print("Quotes balanced:", not in_s and not in_d)
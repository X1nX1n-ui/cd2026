with open(r"D:\3\code\java\src\main\resources\static\pages\assets\hosts.html", "r", encoding="utf-8") as f:
    text = f.read()

# Fix line 887: '条 at end of line missing closing quote
# The broken line:  ? '（当前显示' + ... + '条\n                : '';
# Should be:        ? '（当前显示' + ... + '条'\n                : '';
text = text.replace(
    "+ '\u6761\n                : '';",
    "+ '\u6761'\n                : '';"
)

with open(r"D:\3\code\java\src\main\resources\static\pages\assets\hosts.html", "w", encoding="utf-8") as f:
    f.write(text)

# Verify quotes balanced
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
if in_s: print("  Unclosed single quote!")
if in_d: print("  Unclosed double quote!")
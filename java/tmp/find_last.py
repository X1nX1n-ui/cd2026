with open(r"D:\3\code\java\src\main\resources\static\pages\assets\hosts.html", "r", encoding="utf-8") as f:
    text = f.read()

import re
m = re.search(r"<script>\s*\n\s*layui\.use", text)
js = text[m.start():text.find("</script>", m.start())]

# Find the LAST unclosed quote
in_s = in_d = False
esc = False
last_open_s = -1
last_open_d = -1

for i, c in enumerate(js):
    if esc: esc = False; continue
    if c == '\\': esc = True; continue
    if c == "'" and not in_d:
        if not in_s:
            last_open_s = i
        in_s = not in_s
    elif c == '"' and not in_s:
        if not in_d:
            last_open_d = i
        in_d = not in_d

if in_d:
    # Find HTML line number
    html_pos = m.start() + last_open_d
    line_no = text[:html_pos].count('\n') + 1
    ctx = js[max(0,last_open_d-30):last_open_d+60]
    print("Unclosed double-quote at JS pos %d (HTML line ~%d):" % (last_open_d, line_no))
    print("Context:", repr(ctx))
elif in_s:
    html_pos = m.start() + last_open_s
    line_no = text[:html_pos].count('\n') + 1
    ctx = js[max(0,last_open_s-30):last_open_s+60]
    print("Unclosed single-quote at JS pos %d (HTML line ~%d):" % (last_open_s, line_no))
    print("Context:", repr(ctx))
else:
    print("All balanced!")
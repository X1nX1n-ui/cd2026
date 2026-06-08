with open(r"D:\3\code\java\src\main\resources\static\pages\assets\hosts.html", "r", encoding="utf-8") as f:
    text = f.read()

import re
m = re.search(r"<script>\s*\n\s*layui\.use", text)
js = text[m.start():text.find("</script>", m.start())]

# Track quotes line by line to find where double quote opens
lines = js.split('\n')
in_single = False
in_double = False
escaped = False

for line_no, line in enumerate(lines):
    html_line = line_no + text[:m.start()].count('\n') + 1 + 40  # approximate HTML line
    
    for i, c in enumerate(line):
        if escaped:
            escaped = False
            continue
        if c == '\\':
            escaped = True
            continue
        
        prev_single = in_single
        prev_double = in_double
        
        if c == "'" and not in_double:
            in_single = not in_single
        elif c == '"' and not in_single:
            in_double = not in_double
        
        # If a quote just opened, record it
        if not prev_double and in_double:
            # Show what opened it
            ctx = line[:i+30]
            print("DOUBLE OPEN  at JS line %d: %s" % (line_no+1, ctx.strip()[:100]))
        if prev_double and not in_double:
            ctx = line[max(0,i-20):i+5]
            print("DOUBLE CLOSE at JS line %d: %s" % (line_no+1, ctx.strip()[:80]))
f=open(r"d:\3\code\java\target\classes\static\pages\assets\hosts.html","r",encoding="utf-8")
h=f.read()
f.close()

# Check the init logic - is renderTable called?
for kw in ["layui.use", "renderTable()", "table.render", "init("]:
    idx = h.find(kw)
    if idx > 0:
        ctx = h[max(0,idx-20):idx+80]
        print(f"Found '{kw}' at {idx}: ...{ctx}...")

# Check if there are any JS syntax issues
import re
# Check for malformed regex
for i, line in enumerate(h.split("\n"), 1):
    s = line.strip()
    # Check for common JS errors: missing / in regex, unclosed strings
    if s.count('"') % 2 != 0 and not s.strip().startswith("//"):
        if len(s) > 3 and not s.endswith("+"):
            print(f"Line {i}: Possibly unclosed string: {s[:80]}")
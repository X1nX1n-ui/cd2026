with open(r"d:\3\code\java\src\main\resources\static\pages\assets\hosts.html", "rb") as f:
    data = f.read()

# Check for ???? patterns (4 consecutive 0x3F bytes) which indicate garbled Chinese
import re
# Find all sequences of 4+ consecutive 0x3F bytes
pattern = b'\x3f\x3f\x3f\x3f+'
matches = [(m.start(), m.end()) for m in re.finditer(pattern, data)]
print(f"Found {len(matches)} garbled Chinese spots")
for start, end in matches[:20]:
    ctx_start = max(0, start-20)
    ctx_end = min(len(data), end+20)
    ctx = data[ctx_start:ctx_end]
    print(f"  pos {start}: ...{ctx.decode('utf-8', errors='replace')}...")
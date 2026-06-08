with open(r"d:\3\code\java\src\main\resources\static\pages\assets\hosts.html", "rb") as f:
    data = f.read()

# Find sequences of 4+ consecutive 0x3F bytes
i = 0
garbled = []
while i < len(data):
    if data[i] == 0x3F:
        start = i
        while i < len(data) and data[i] == 0x3F:
            i += 1
        length = i - start
        if length >= 4:
            garbled.append((start, length))
    else:
        i += 1

print(f"Found {len(garbled)} garbled Chinese spots (4+ consecutive ? bytes)")
for start, length in garbled[:30]:
    ctx_start = max(0, start-15)
    ctx_end = min(len(data), start+length+15)
    ctx = data[ctx_start:ctx_end]
    print(f"  pos {start}, len={length}: ...{ctx.decode('utf-8', errors='replace')}...")
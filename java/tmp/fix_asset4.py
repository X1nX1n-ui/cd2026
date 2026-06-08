with open(r"d:\3\code\java\src\main\resources\static\pages\assets\asset-detail.html", "r", encoding="utf-8-sig") as f:
    content = f.read()

changes = []

# Fix normalized === " in low context
old = 'normalized === ") {\n                return "low"'
new = 'normalized === "\u4f4e") {\n                return "low"'
if old in content:
    content = content.replace(old, new)
    changes.append("1. normalizedLow")

# Fix normalized === " in safe context
old = 'normalized === ") {\n                return "safe"'
new = 'normalized === "\u65e0") {\n                return "safe"'
if old in content:
    content = content.replace(old, new)
    changes.append("2. normalizedSafe")

# Fix third risk check: "风险等级：") with return "high" -> "风险等级:高")
old = 'resultText.indexOf("\u98ce\u9669\u7b49\u7ea7\uff1a") >= 0) {\n                return "high"'
new = 'resultText.indexOf("\u98ce\u9669\u7b49\u7ea7:\u9ad8") >= 0) {\n                return "high"'
if old in content:
    content = content.replace(old, new)
    changes.append("3. highRiskLevel")

# Fix third risk check: return "medium" -> "风险等级:中")
old = 'resultText.indexOf("\u98ce\u9669\u7b49\u7ea7\uff1a") >= 0) {\n                return "medium"'
new = 'resultText.indexOf("\u98ce\u9669\u7b49\u7ea7:\u4e2d") >= 0) {\n                return "medium"'
if old in content:
    content = content.replace(old, new)
    changes.append("4. mediumRiskLevel")

# Fix third risk check: return "low" -> "风险等级:低")
old = 'resultText.indexOf("\u98ce\u9669\u7b49\u7ea7\uff1a") >= 0) {\n                return "low"'
new = 'resultText.indexOf("\u98ce\u9669\u7b49\u7ea7:\u4f4e") >= 0) {\n                return "low"'
if old in content:
    content = content.replace(old, new)
    changes.append("5. lowRiskLevel")

# Fix third risk check: return "safe" -> "风险等级:无")
old = 'resultText.indexOf("\u98ce\u9669\u7b49\u7ea7\uff1a") >= 0) {\n                return "safe"'
new = 'resultText.indexOf("\u98ce\u9669\u7b49\u7ea7:\u65e0") >= 0) {\n                return "safe"'
if old in content:
    content = content.replace(old, new)
    changes.append("6. safeRiskLevel")

with open(r"d:\3\code\java\src\main\resources\static\pages\assets\asset-detail.html", "w", encoding="utf-8-sig") as f:
    f.write(content)

print(f"Applied {len(changes)} fixes:")
for c in changes:
    print(f"  {c}")

# Print what didn't match
all_olds = [
    ('normalized === ") {\n                return "low"', "normalizedLow"),
    ('normalized === ") {\n                return "safe"', "normalizedSafe"),
]
for o, name in all_olds:
    if o not in content:
        # Try with \r\n
        o2 = o.replace('\n', '\r\n')
        if o2 in content:
            print(f"  NOTE: {name} uses CRLF")
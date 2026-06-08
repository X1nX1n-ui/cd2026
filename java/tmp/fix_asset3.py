with open(r"d:\3\code\java\src\main\resources\static\pages\assets\asset-detail.html", "r", encoding="utf-8-sig") as f:
    content = f.read()

changes = []

# Fix 1: Line 320 advice regex: (?:加固建议|建议")[] -> (?:加固建议|建议)[：:]
old = '(\u52a0\u56fa\u5efa\u8bae|\u5efa\u8bae")[]'
new = '(\u52a0\u56fa\u5efa\u8bae|\u5efa\u8bae)[\uff1a:]'
if old in content:
    content = content.replace(old, new)
    changes.append("1. adviceRegex fixed")
else:
    print(f"NOT FOUND 1: trying alternate patterns")
    # Try without the outer parens
    if '\u5efa\u8bae")[]' in content:
        content = content.replace('\u5efa\u8bae")[]', '\u5efa\u8bae)[\uff1a:]')
        # But this changes only the second part, need to fix the whole alternation
        # Actually let me be more precise
        pass

# Fix 2: resultText.indexOf("高风) -> resultText.indexOf("高风险")
old = '\u9ad8\u98ce)'
new = '\u9ad8\u98ce\u9669")'
if old in content:
    content = content.replace(old, new)
    changes.append("2. highRisk fixed")

# Fix 3: resultText.indexOf("中风) -> resultText.indexOf("中风险")
old = '\u4e2d\u98ce)'
new = '\u4e2d\u98ce\u9669")'
if old in content:
    content = content.replace(old, new)
    changes.append("3. mediumRisk fixed")

# Fix 4: normalized === ") in low context -> normalized === "低")
old = 'normalized === ")\u007b\n                return "low"'
new = 'normalized === "\u4f4e")\u007b\n                return "low"'
if old in content:
    content = content.replace(old, new)
    changes.append("4. normalizedLow fixed")

# Fix 5: normalized === ") in safe context -> normalized === "无")
old = 'normalized === ")\u007b\n                return "safe"'
new = 'normalized === "\u65e0")\u007b\n                return "safe"'
if old in content:
    content = content.replace(old, new)
    changes.append("5. normalizedSafe fixed")

# Fix 6-9: Third risk check in each block uses generic "风险等级：") - need context-specific
# High: "风险等级：") with return "high" -> "风险等级:高")
old = 'resultText.indexOf("\u98ce\u9669\u7b49\u7ea7\uff1a")\u007d >= 0)\u007b\n                return "high"'
new = 'resultText.indexOf("\u98ce\u9669\u7b49\u7ea7:\u9ad8") >= 0)\u007b\n                return "high"'
if old in content:
    content = content.replace(old, new)
    changes.append("6. highResultRiskLevel fixed")

# Medium: "风险等级：") with return "medium" -> "风险等级:中")
old = 'resultText.indexOf("\u98ce\u9669\u7b49\u7ea7\uff1a")\u007d >= 0)\u007b\n                return "medium"'
new = 'resultText.indexOf("\u98ce\u9669\u7b49\u7ea7:\u4e2d") >= 0)\u007b\n                return "medium"'
if old in content:
    content = content.replace(old, new)
    changes.append("7. mediumResultRiskLevel fixed")

# Low: "风险等级：") with return "low" -> "风险等级:低")
old = 'resultText.indexOf("\u98ce\u9669\u7b49\u7ea7\uff1a")\u007d >= 0)\u007b\n                return "low"'
new = 'resultText.indexOf("\u98ce\u9669\u7b49\u7ea7:\u4f4e") >= 0)\u007b\n                return "low"'
if old in content:
    content = content.replace(old, new)
    changes.append("8. lowResultRiskLevel fixed")

# Safe: "风险等级：") with return "safe" -> "风险等级:无")
old = 'resultText.indexOf("\u98ce\u9669\u7b49\u7ea7\uff1a")\u007d >= 0)\u007b\n                return "safe"'
new = 'resultText.indexOf("\u98ce\u9669\u7b49\u7ea7:\u65e0") >= 0)\u007b\n                return "safe"'
if old in content:
    content = content.replace(old, new)
    changes.append("9. safeResultRiskLevel fixed")

with open(r"d:\3\code\java\src\main\resources\static\pages\assets\asset-detail.html", "w", encoding="utf-8-sig") as f:
    f.write(content)

print(f"Applied {len(changes)} fixes:")
for c in changes:
    print(f"  {c}")
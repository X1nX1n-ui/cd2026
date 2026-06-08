import re

with open(r"d:\3\code\java\src\main\resources\static\pages\assets\asset-detail.html", "r", encoding="utf-8-sig") as f:
    content = f.read()

changes = []

# Fix 1: '\uff08\u5f53\u524d\u663e' -> '\uff08\u5f53\u524d\u663e\u793a' (（当前显 -> （当前显示)
# Use ASCII quote (0x27) not fancy quote
old = "'\uff08\u5f53\u524d\u663e'"
new = "'\uff08\u5f53\u524d\u663e\u793a'"
count = content.count(old)
if count:
    content = content.replace(old, new)
    changes.append(f"1. currentDisplay ({count}x)")

# Fix 2: regex pattern \u5efa\u8bae" -> \u5efa\u8bae (建议" -> 建议)
old = '\u5efa\u8bae"'
new = '\u5efa\u8bae'
# But only in the specific regex context
# Check line 320: (?:加固建议|建议") -> (?:加固建议|建议)  
old2 = '(\u52a0\u56fa\u5efa\u8bae|\u5efa\u8bae")'
new2 = '(\u52a0\u56fa\u5efa\u8bae|\u5efa\u8bae)'
count2 = content.count(old2)
if count2:
    content = content.replace(old2, new2)
    changes.append(f"2. adviceRegex ({count2}x)")

# Fix 3: Empty character classes [] -> [\uff1a:] (Chinese colon or ASCII colon)
# Pattern: 风险原因[] -> 风险原因[\uff1a:]
old3 = '\u98ce\u9669\u539f\u56e0[]'
new3 = '\u98ce\u9669\u539f\u56e0[\uff1a:]'
count3 = content.count(old3)
if count3:
    content = content.replace(old3, new3)
    changes.append(f"3a. riskReasonRegex ({count3}x)")

old3b = '\u52a0\u56fa\u5efa\u8bae[]'
new3b = '\u52a0\u56fa\u5efa\u8bae[\uff1a:]'
count3b = content.count(old3b)
if count3b:
    content = content.replace(old3b, new3b)
    changes.append(f"3b. adviceColonRegex ({count3b}x)")

old3c = '\u5efa\u8bae[]'
new3c = '\u5efa\u8bae[\uff1a:]'
count3c = content.count(old3c)
if count3c:
    content = content.replace(old3c, new3c)
    changes.append(f"3c. suggestColonRegex ({count3c}x)")

# Fix 4: "\u4f4e\u98ce) -> "\u4f4e\u98ce\u9669") (低风) -> 低风险")
old4 = '\u4f4e\u98ce)'
new4 = '\u4f4e\u98ce\u9669")'
count4 = content.count(old4)
if count4:
    content = content.replace(old4, new4)
    changes.append(f"4. lowRisk ({count4}x)")

# Fix 5: normalized === " -> normalized === "\u4f4e" or "\u65e0"
# Need to be more careful here - check context
old5a = 'normalized === ")\u007b\n                return "low"'
new5a = 'normalized === "\u4f4e")\u007b\n                return "low"'
if old5a in content:
    content = content.replace(old5a, new5a)
    changes.append("5a. normalizedLow")

old5b = 'normalized === ")\u007b\n                return "safe"'
new5b = 'normalized === "\u65e0")\u007b\n                return "safe"'
if old5b in content:
    content = content.replace(old5b, new5b)
    changes.append("5b. normalizedSafe")

# Fix 6: "\u98ce\u9669\u7b49\u7ea7:) -> "\u98ce\u9669\u7b49\u7ea7\uff1a") or "\u98ce\u9669\u7b49\u7ea7:")
old6 = '\u98ce\u9669\u7b49\u7ea7:)'
new6 = '\u98ce\u9669\u7b49\u7ea7\uff1a")'
count6 = content.count(old6)
if count6:
    content = content.replace(old6, new6)
    changes.append(f"6. riskLevel ({count6}x)")

# Fix 7: resultText.indexOf("\u4f4e\u98ce) -> resultText.indexOf("\u4f4e\u98ce\u9669")
old7 = '("\u4f4e\u98ce)'
new7 = '("\u4f4e\u98ce\u9669")'
count7 = content.count(old7)
if count7:
    content = content.replace(old7, new7)
    changes.append(f"7. resultLowRisk ({count7}x)")

# Fix 8: Check if there are other broken "风险等级:" patterns
old8 = '\u98ce\u9669\u7b49\u7ea7:")'
# This might be the correctly-fixed version
# Let me check what's left

with open(r"d:\3\code\java\src\main\resources\static\pages\assets\asset-detail.html", "w", encoding="utf-8-sig") as f:
    f.write(content)

print(f"Applied {len(changes)} fixes:")
for c in changes:
    print(f"  {c}")
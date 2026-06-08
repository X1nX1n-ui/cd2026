import re

with open(r"d:\3\code\java\src\main\resources\static\pages\assets\asset-detail.html", "r", encoding="utf-8-sig") as f:
    content = f.read()

changes = []

# All fixes use \u escapes to avoid PowerShell encoding issues
fixes = [
    # Risk level map: "\u9ad8\u98ce: "high"" -> "\u9ad8\u98ce\u9669": "high" (高风 -> 高风险)
    ('"\u9ad8\u98ce: "high"', '"\u9ad8\u98ce\u9669": "high"'),
    ('"\u4e2d\u98ce: "medium"', '"\u4e2d\u98ce\u9669": "medium"'),
    ('"\u4f4e\u98ce: "low"', '"\u4f4e\u98ce\u9669": "low"'),
    # "\u672a\u547d\u540d\u4e3b) -> \u672a\u547d\u540d\u4e3b\u673a") (未命名主 -> 未命名主机)
    ('\u672a\u547d\u540d\u4e3b)', '\u672a\u547d\u540d\u4e3b\u673a")'),
    # "\u6700\u540e\u4e0a, -> "\u6700\u540e\u4e0a\u7ebf", (最后上 -> 最后上线)
    ('\u6700\u540e\u4e0a,', '\u6700\u540e\u4e0a\u7ebf",'),
    # '\uff08\u5f53\u524d\u663e' -> '\uff08\u5f53\u524d\u663e\u793a' (（当前显 -> （当前显示)
    ("\u2018\uff08\u5f53\u524d\u663e\u2019", "\u2018\uff08\u5f53\u524d\u663e\u793a\u2019"),
    # "\u5efa\u8bae) -> "\u5efa\u8bae") (建议 -> 建议")
    ('\u5efa\u8bae)', '\u5efa\u8bae")'),
    # "\u9ad8\u98ce + panelLabel -> "\u9ad8\u98ce\u9669" + panelLabel (高风 -> 高风险)
    ('\u9ad8\u98ce + panelLabel', '\u9ad8\u98ce\u9669" + panelLabel'),
    ('\u4e2d\u98ce + panelLabel', '\u4e2d\u98ce\u9669" + panelLabel'),
    ('\u4f4e\u98ce + panelLabel', '\u4f4e\u98ce\u9669" + panelLabel'),
    # "\u672a\u5206; -> "\u672a\u5206\u7ea7"; (未分 -> 未分级)
    ('\u672a\u5206;', '\u672a\u5206\u7ea7";'),
    # '\u672a\u53d1\u73b0\u9ad8\u98ce\u9669; -> ...\u9879'; (未发现高风险 -> 未发现高风险项)
    ('\u672a\u53d1\u73b0\u9ad8\u98ce\u9669;', '\u672a\u53d1\u73b0\u9ad8\u98ce\u9669\u9879\';'),
    # Field labels
    ('\u6700\u540e\u767b\u5f55\u65f6', '\u6700\u540e\u767b\u5f55\u65f6\u95f4"'),  # 最后登录时 -> 最后登录时间"
    ('\u53ef\u6267\u884c\u8def', '\u53ef\u6267\u884c\u8def\u5f84"'),  # 可执行路 -> 可执行路径"
    ('\u547d\u4ee4,', '\u547d\u4ee4\u884c",'),  # 命令, -> 命令行",
    ('\u53d1\u5e03,', '\u53d1\u5e03\u8005",'),  # 发布, -> 发布者",
    ('executable_path: \u53ef\u6267\u884c\u8def', 'executable_path: \u53ef\u6267\u884c\u8def\u5f84"'),  # executable_path: 可执行路 -> executable_path: 可执行路径"
    ('command_line: \u547d\u4ee4,', 'command_line: \u547d\u4ee4\u884c",'),  # command_line: 命令, -> command_line: 命令行",
    ('publisher: \u53d1\u5e03,', 'publisher: \u53d1\u5e03\u8005",'),  # publisher: 发布, -> publisher: 发布者",
]

for old, new in fixes:
    if old in content:
        content = content.replace(old, new)
        changes.append(f"fixed: {old[:40]}...")
    else:
        print(f"NOT FOUND: {old[:40]}...")

with open(r"d:\3\code\java\src\main\resources\static\pages\assets\asset-detail.html", "w", encoding="utf-8-sig") as f:
    f.write(content)

print(f"\nApplied {len(changes)} fixes")
for c in changes:
    print(f"  {c}")
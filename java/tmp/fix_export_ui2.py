with open(r"d:\3\code\java\src\main\resources\static\pages\assets\hosts.html", "rb") as f:
    data = f.read()

C = lambda s: s.encode("utf-8")
rn = b"\r\n"
changes = 0

# 1. Checkbox column
old_cols = b"cols: [[\n                    {field: \"id\", title: \"ID\", width: 80},"
new_cols = b"cols: [[\n                    {type: \"checkbox\", fixed: \"left\"},\n                    {field: \"id\", title: \"ID\", width: 80},"
data = data.replace(old_cols, new_cols)
changes += 1
print("1. Checkbox OK")

# 2. Export button
old_btn = b'id="openProbeScheduleBtn" style="margin-left:8px;">' + C('探测策略') + b'</button>'
new_btn = b'id="openProbeScheduleBtn" style="margin-left:8px;">' + C('探测策略') + b'</button>\n                    <button type="button" class="layui-btn page-secondary-btn host-toolbar-btn" id="batchExportBtn" style="margin-left:8px;">' + C('导出资产清单') + b'</button>'
data = data.replace(old_btn, new_btn)
changes += 1
print("2. Button OK")

# 3. Export JS (read from file)
with open(r"d:\3\code\java\tmp\export_dialog.js", "rb") as f:
    export_js = f.read()

marker = b"function configureAutoRefresh(value) {"
data = data.replace(marker, export_js + rn + b"        " + marker)
changes += 1
print("3. JS OK")

print(f"Total: {changes}")
with open(r"d:\3\code\java\src\main\resources\static\pages\assets\hosts.html", "wb") as f:
    f.write(data)
with open(r"d:\3\code\java\src\main\resources\static\pages\assets\hosts.html", "rb") as f:
    data = f.read()

C = lambda s: s.encode("utf-8")
rn = b"\r\n"

# Change save button from page-btn to page-secondary-btn for consistency
old = (
    b"<button type=\"button\" class=\"layui-btn layui-btn-sm page-btn\" id=\"scheduleSaveBtn\" style=\"min-width:100px;\">" + C("保存配置") + b"</button>'"
)
new = (
    b"<button type=\"button\" class=\"layui-btn layui-btn-sm page-secondary-btn\" id=\"scheduleSaveBtn\" style=\"min-width:100px;\">" + C("保存配置") + b"</button>'"
)

if old in data:
    data = data.replace(old, new)
    print("Save button now uses page-secondary-btn (42px)")
else:
    print("NOT FOUND")

with open(r"d:\3\code\java\src\main\resources\static\pages\assets\hosts.html", "wb") as f:
    f.write(data)